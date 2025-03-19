// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal;

import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.idea.LoggerFactory;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.ide.bootstrap.StartupUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@ApiStatus.Internal
public class OpenLogAction extends DumbAwareAction {
  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(e.getProject() != null);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;

    openLogInEditor(project);
  }

  public static void openLogInEditor(@NotNull Project project) {
    VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(LoggerFactory.getLogFilePath());
    if (file != null) {
      VfsUtil.markDirtyAndRefresh(true, false, false, file);
      final FileEditor[] editors = FileEditorManager.getInstance(project).openFile(file, true);
      if (editors.length > 0 && editors[0] instanceof TextEditor) {
        scrollToLastIDEStart((TextEditor)editors[0]);
      }
      else {
        PsiNavigationSupport.getInstance().createNavigatable(project, file, -1).navigate(true);
      }
    }
    else {
      String title = "Cannot find '" + LoggerFactory.getLogFilePath() + "'";
      Notifications.Bus.notify(new Notification(Notifications.SYSTEM_MESSAGES_GROUP_ID, title, "", NotificationType.INFORMATION));
    }
  }

  private static void scrollToLastIDEStart(TextEditor editor) {
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        byte[] bytes = editor.getFile().contentsToByteArray(true);
        String log = new String(bytes, StandardCharsets.UTF_8);
        int index = log.lastIndexOf(StartupUtil.IDE_STARTED);
        if (index != -1) {
          ApplicationManager.getApplication().invokeLater(() -> {
            editor.getEditor().getCaretModel().moveToOffset(index);
            editor.getEditor().getScrollingModel().scrollToCaret(ScrollType.CENTER_UP);
          }, x -> editor.getEditor().isDisposed());
        }
      } catch (IOException ignore) { }
    });
  }
}
