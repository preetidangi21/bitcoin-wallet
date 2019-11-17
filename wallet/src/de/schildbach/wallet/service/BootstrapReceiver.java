/*
 * Copyright the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.schildbach.wallet.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import androidx.annotation.WorkerThread;
import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import org.bitcoinj.wallet.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Andreas Schildbach
 */
public class BootstrapReceiver extends BroadcastReceiver {
    private static final Logger log = LoggerFactory.getLogger(BootstrapReceiver.class);

    @Override
    public void onReceive(final Context context, final Intent intent) {
        log.info("got broadcast: " + intent);
        final PendingResult result = goAsync();
        AsyncTask.execute(() -> {
            org.bitcoinj.core.Context.propagate(Constants.CONTEXT);
            onAsyncReceive(context, intent);
            result.finish();
        });
    }

    @WorkerThread
    private void onAsyncReceive(final Context context, final Intent intent) {
        final WalletApplication application = (WalletApplication) context.getApplicationContext();

        final boolean bootCompleted = Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction());
        final boolean packageReplaced = Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction());

        if (packageReplaced || bootCompleted) {
            // make sure wallet is upgraded to HD
            if (packageReplaced)
                maybeUpgradeWallet(application.getWallet());

            // make sure there is always a blockchain sync scheduled
            StartBlockchainService.schedule(application, true);

            // if the app hasn't been used for a while and contains coins, maybe show reminder
            final Configuration config = application.getConfiguration();
            if (config.remindBalance() && config.hasBeenUsed()
                    && config.getLastUsedAgo() > Constants.LAST_USAGE_THRESHOLD_INACTIVE_MS)
                InactivityNotificationService.startMaybeShowNotification(context);
        }
    }

    @WorkerThread
    private void maybeUpgradeWallet(final Wallet wallet) {
        log.info("maybe upgrading wallet");

        // Maybe upgrade wallet from basic to deterministic, and maybe upgrade to the latest script type
        if (wallet.isDeterministicUpgradeRequired(Constants.UPGRADE_OUTPUT_SCRIPT_TYPE) && !wallet.isEncrypted())
            wallet.upgradeToDeterministic(Constants.UPGRADE_OUTPUT_SCRIPT_TYPE, null);

        // Maybe upgrade wallet to secure chain
        try {
            wallet.doMaintenance(null, false);
        } catch (final Exception x) {
            log.error("failed doing wallet maintenance", x);
        }
    }
}
