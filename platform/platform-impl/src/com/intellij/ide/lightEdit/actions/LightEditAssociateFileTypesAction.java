// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit.actions;

import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.ide.lightEdit.actions.associate.FileAssociationException;
import com.intellij.ide.lightEdit.actions.associate.SystemAssociatorFactory;
import com.intellij.ide.lightEdit.actions.associate.SystemFileTypeAssociator;
import com.intellij.ide.lightEdit.actions.associate.ui.FileTypeAssociationDialog;
import com.intellij.idea.ActionsBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("ComponentNotRegistered")
public class LightEditAssociateFileTypesAction extends DumbAwareAction implements LightEditCompatible {
  public final static String ENABLE_REG_KEY =  "system.file.type.associations.enabled";

  private final static String NOTIFICATION_GROUP_ID = "associate.files";

  public LightEditAssociateFileTypesAction() {
    super(getActionTitle());
  }

  private static String getActionTitle() {
    return ActionsBundle.message(
      "action.LightEditAssociateFileTypesAction.text",
      ApplicationNamesInfo.getInstance().getFullProductName());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    SystemFileTypeAssociator associator = SystemAssociatorFactory.getAssociator();
    if (associator != null) {
      FileTypeAssociationDialog dialog = new FileTypeAssociationDialog();
      if (dialog.showAndGet()) {
        ApplicationManager.getApplication().executeOnPooledThread(
          () -> {
            try {
              SystemAssociatorFactory.getAssociator().associateFileTypes(dialog.getSelectedFileTypes());
              notifyOnSuccess();
            }
            catch (FileAssociationException exception) {
              notifyOnError(exception.getMessage());
            }
          }
        );
      }
    }
  }

  private static void notifyOnError(@NotNull String message) {
    ApplicationManager.getApplication().invokeLater(
      () -> Notifications.Bus.notify(new Notification(
        NOTIFICATION_GROUP_ID,
        ApplicationBundle.message("light.edit.file.types.open.with.error"), message, NotificationType.ERROR)));
  }

  private static void notifyOnSuccess() {
    ApplicationManager.getApplication().invokeLater(
      () -> Notifications.Bus.notify(new Notification(
        NOTIFICATION_GROUP_ID,
        ApplicationBundle.message("light.edit.file.types.open.with.success.title"),
        ApplicationBundle.message("light.edit.file.types.open.with.success.message",
                                  ApplicationInfo.getInstance().getFullApplicationName()),
        NotificationType.INFORMATION)));
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(isAvailable());
  }

  public static boolean isAvailable() {
    return Registry.get(ENABLE_REG_KEY).asBoolean() && SystemAssociatorFactory.getAssociator() != null;
  }
}
