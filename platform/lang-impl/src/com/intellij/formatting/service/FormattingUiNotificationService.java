// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting.service;

import com.intellij.CodeStyleBundle;
import com.intellij.formatting.FormattingContext;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

final class FormattingUiNotificationService implements FormattingNotificationService {

  private final @NotNull Project myProject;

  FormattingUiNotificationService(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public void reportError(@NotNull String groupId,
                          @NotNull @NlsContexts.NotificationTitle String title,
                          @NotNull @NlsContexts.NotificationContent String message) {
    Notifications.Bus.notify(new Notification(groupId, title, message, NotificationType.ERROR), myProject);
  }

  @Override
  public void reportError(@NotNull String groupId,
                          @NotNull @NlsContexts.NotificationTitle String title,
                          @NotNull @NlsContexts.NotificationContent String message, AnAction... actions) {
    Notification notification = new Notification(groupId, title, message, NotificationType.ERROR);
    notification.addActions(List.of(actions));
    Notifications.Bus.notify(notification, myProject);
  }

  @Override
  public void reportErrorAndNavigate(@NotNull String groupId,
                                     @NotNull String title,
                                     @NotNull String message,
                                     @NotNull FormattingContext context,
                                     int offset) {
    VirtualFile virtualFile = context.getVirtualFile();
    if (virtualFile != null) {
      ApplicationManager.getApplication().invokeLater(
        () -> {
          FileEditor[] editors = FileEditorManager.getInstance(myProject).getEditors(virtualFile);
          if (editors.length > 0) {
            reportError(groupId, title, message);
            FileEditor textEditor = ContainerUtil.find(editors, editor -> editor instanceof TextEditor);
            if (textEditor != null) {
              navigateToFile(virtualFile, offset);
            }
          }
          else {
            Notification notification = new Notification(groupId, title, message, NotificationType.ERROR);
            notification.addAction(new DumbAwareAction(CodeStyleBundle.message("formatting.service.open.file", virtualFile.getName())) {
              @Override
              public void actionPerformed(@NotNull AnActionEvent e) {
                navigateToFile(virtualFile, offset);
              }
            });
            Notifications.Bus.notify(notification, myProject);
          }
        }
      );
    }
  }

  private void navigateToFile(@NotNull VirtualFile file, int offset) {
    OpenFileDescriptor descriptor = new OpenFileDescriptor(myProject, file, offset);
    FileEditorManager.getInstance(myProject).openTextEditor(descriptor, true);
  }
}
