package com.intellij.diagnostic;

import com.intellij.diagnostic.errordialog.DisablePluginWarningDialog;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.notification.*;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.util.Set;

/** Static utility to notify the Android Studio user of errors from non-bundled plugins. */
class PluginErrorNotifications {
  private static final NotificationGroup NOTIFICATION_GROUP =
    new NotificationGroup("Plugin Errors", NotificationDisplayType.STICKY_BALLOON, true);
  private static final Set<String> myPluginsShowing = new HashSet<String>();

  private PluginErrorNotifications() {}

  static void maybeNotifyUi(@NotNull LogMessage message) {
    final PluginId pluginId = IdeErrorsDialog.findPluginId(message.getThrowable());
    if (pluginId == null || myPluginsShowing.contains(pluginId.getIdString()) || PluginManager.getPlugin(pluginId).isBundled()) {
      return;
    }
    myPluginsShowing.add(pluginId.getIdString());

    String title = "Plugin Error";
    String notificationText = String.format("%s threw an uncaught %s. <a href='xxx'>Disable Plugin</a>",
                                            PluginManager.getPlugin(pluginId).getName(),
                                            message.getThrowable().getClass().getSimpleName());
    NotificationListener listener = new NotificationListener() {
      @Override
      public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
        JRootPane rootPane = WindowManager.getInstance().findVisibleFrame().getRootPane();
        DisablePluginWarningDialog.disablePlugin(pluginId, rootPane);
      }
    };
    Notification notification =
      new Notification(NOTIFICATION_GROUP.getDisplayId(), title, notificationText, NotificationType.ERROR, listener) {
        @Override
        public void expire() {
          super.expire();
          myPluginsShowing.remove(pluginId.getIdString());
        }
      };
    notification.notify(null);
    message.setNotification(notification);
  }
}
