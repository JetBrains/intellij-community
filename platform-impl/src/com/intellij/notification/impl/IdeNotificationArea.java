package com.intellij.notification.impl;

import com.intellij.notification.*;
import com.intellij.notification.impl.ui.NotificationComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.wm.impl.status.StatusBarImpl;
import com.intellij.openapi.wm.impl.status.StatusBarPatch;
import com.intellij.openapi.wm.impl.status.StatusBarTooltipper;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author spleaner
 */
public class IdeNotificationArea implements Notifications, StatusBarPatch {
  private NotificationModel myModel = new NotificationModel();
  private NotificationComponent myNotificationComponent;

  public IdeNotificationArea(final StatusBarImpl statusBar) {
    myNotificationComponent = new NotificationComponent(this);

    final MessageBus messageBus = ApplicationManager.getApplication().getMessageBus();
    messageBus.connect().subscribe(TOPIC, this);

    StatusBarTooltipper.install(this, statusBar);
  }

  public void register(@NotNull final String componentName, @NotNull final NotificationDisplayType defaultDisplayType, final boolean canDisable) {
    // do nothing
  }

  public void notify(@NotNull final String componentName, @NotNull final String id, @NotNull final String description, @NotNull final NotificationType type, @NotNull final NotificationListener handler) {
    notify(componentName, id, description, type, handler, null);
  }

  public void notify(@NotNull final String componentName, @NotNull final String id, @NotNull final String description, @NotNull final NotificationType type, @NotNull final NotificationListener handler, @Nullable final Icon icon) {
    myModel.add(new NotificationImpl(componentName, id, description, type, icon, handler));
  }

  public NotificationModel getModel() {
    return myModel;
  }

  public JComponent getComponent() {
    return myNotificationComponent;
  }

  public String updateStatusBar(final Editor selected, final JComponent componentSelected) {
    return null;
  }

  public void clear() {
  }
}
