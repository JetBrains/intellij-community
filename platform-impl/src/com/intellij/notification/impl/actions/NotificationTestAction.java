package com.intellij.notification.impl.actions;

import com.intellij.notification.*;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;

/**
 * @author spleaner
 */
public class NotificationTestAction extends AnAction {
  public NotificationTestAction() {
    super("Add Test Notification");

    final MessageBus messageBus = ApplicationManager.getApplication().getMessageBus();
    messageBus.syncPublisher(Notifications.TOPIC).register("IDEA", NotificationDisplayType.BALOON, false);
  }

  public void actionPerformed(final AnActionEvent e) {
    final MessageBus messageBus = ApplicationManager.getApplication().getMessageBus();
    messageBus.syncPublisher(Notifications.TOPIC).notify("IDEA", "Test Notification", "Test Notification Description", NotificationType.INFORMATION,
        new NotificationListener() {
          @NotNull
          public OnClose perform() {
            final int i = Messages.showChooseDialog("Notification message", "Test", new String[]{"Leave", "Remove"}, "Remove", null);
            return i == 1 ? OnClose.REMOVE : OnClose.LEAVE;
          }
        });
  }
}
