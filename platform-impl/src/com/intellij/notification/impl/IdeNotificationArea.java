package com.intellij.notification.impl;

import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.notification.impl.ui.NotificationComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.wm.impl.status.StatusBarImpl;
import com.intellij.openapi.wm.impl.status.StatusBarPatch;
import com.intellij.openapi.wm.impl.status.StatusBarTooltipper;
import com.intellij.openapi.project.Project;
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

    StatusBarTooltipper.install(this, statusBar);
  }

  public void register(@NotNull final String id, @NotNull final NotificationDisplayType defaultDisplayType, final boolean canDisable) {
  }

  public void notify(@NotNull final String id, @NotNull final String name, @NotNull final String description, @NotNull final NotificationType type, @NotNull final NotificationListener handler) {
    notify(id, name, description, type, handler, null);
  }

  public void notify(@NotNull final String id, @NotNull final String name, @NotNull final String description, @NotNull final NotificationType type, @NotNull final NotificationListener handler, @Nullable final Icon icon) {
    myModel.add(new NotificationImpl(id, name, description, type, icon, handler));
  }

  public void invalidateAll(@NotNull final String id) {
    myModel.invalidateAll(id);
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

  public void connect(final Project project) {
    project.getMessageBus().connect().subscribe(TOPIC, this);
  }
}
