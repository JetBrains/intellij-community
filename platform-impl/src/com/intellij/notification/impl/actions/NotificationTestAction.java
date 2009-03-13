package com.intellij.notification.impl.actions;

import com.intellij.notification.*;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;

/**
 * @author spleaner
 */
public class NotificationTestAction extends AnAction {
  public NotificationTestAction() {
    super("Add Test Notification");
  }

  public void actionPerformed(final AnActionEvent e) {
    final Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    final MessageBus messageBus = project == null ? ApplicationManager.getApplication().getMessageBus() : project.getMessageBus();
    messageBus.syncPublisher(Notifications.TOPIC).notify("Idea.Test", "Test Notification", "Test Notification Description", NotificationType.INFORMATION,
        new NotificationListener() {
          @NotNull
          public OnClose perform() {
            final int i = Messages.showChooseDialog("Notification message", "Test", new String[]{"Leave", "Remove"}, "Remove", null);
            return i == 1 ? OnClose.REMOVE : OnClose.LEAVE;
          }
        });
  }
}
