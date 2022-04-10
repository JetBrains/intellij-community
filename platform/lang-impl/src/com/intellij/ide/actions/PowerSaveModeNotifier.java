// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.PowerSaveMode;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class PowerSaveModeNotifier implements StartupActivity.DumbAware {
  private static final String IGNORE_POWER_SAVE_MODE = "ignore.power.save.mode";

  @Override
  public void runActivity(@NotNull Project project) {
    if (PowerSaveMode.isEnabled()) {
      notifyOnPowerSaveMode(project);
    }
  }

  static void notifyOnPowerSaveMode(@Nullable Project project) {
    if (PropertiesComponent.getInstance().getBoolean(IGNORE_POWER_SAVE_MODE)) {
      return;
    }

    Notification notification = NotificationGroupManager.getInstance().getNotificationGroup("Power Save Mode").createNotification(
      IdeBundle.message("power.save.mode.on.notification.title"),
      IdeBundle.message("power.save.mode.on.notification.content"),
      NotificationType.WARNING
    );

    notification.addAction(new NotificationAction(IdeBundle.message("action.Anonymous.text.do.not.show.again")) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
        PropertiesComponent.getInstance().setValue(IGNORE_POWER_SAVE_MODE, true);
        notification.expire();
      }
    });
    notification.addAction(new NotificationAction(IdeBundle.message("power.save.mode.disable.action.title")) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
        PowerSaveMode.setEnabled(false);
        notification.expire();
      }
    });

    notification.notify(project);

    Balloon balloon = notification.getBalloon();
    if (balloon != null) {
      MessageBus bus = project == null ? ApplicationManager.getApplication().getMessageBus() : project.getMessageBus();
      MessageBusConnection connection = bus.connect();
      Disposer.register(balloon, connection);
      connection.subscribe(PowerSaveMode.TOPIC, () -> notification.expire());
    }
  }
}
