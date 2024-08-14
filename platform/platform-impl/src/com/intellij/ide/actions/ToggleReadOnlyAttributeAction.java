// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.CommonBundle;
import com.intellij.idea.ActionsBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.io.ReadOnlyAttributeUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.stream.Stream;

public final class ToggleReadOnlyAttributeAction extends AnAction implements DumbAware {
  @Override
  public void update(@NotNull AnActionEvent e) {
    VirtualFile[] files = getFiles(e.getDataContext());
    e.getPresentation().setEnabledAndVisible(files.length > 0);
    if (files.length > 0) {
      int ro = 0, rw = 0, f = 0, d = 0;
      for (VirtualFile file : files) {
        if (file.isWritable()) ++rw; else ++ro;
        if (file.isDirectory()) ++d; else ++f;
      }
      int finalRo = ro;
      int finalRw = rw;
      int finalF = f;
      int finalD = d;
      if (ro > 0 && rw > 0) {
        e.getPresentation().setText(ActionsBundle.messagePointer("action.ToggleReadOnlyAttribute.text"));
      }
      else if (f > 0 && d == 0) {
        e.getPresentation().setText(ActionsBundle.messagePointer("action.ToggleReadOnlyAttribute.files", finalRo, finalRw, finalF, finalD));
      }
      else if (f == 0 && d > 0) {
        e.getPresentation().setText(ActionsBundle.messagePointer("action.ToggleReadOnlyAttribute.dirs", finalRo, finalRw, finalF, finalD));
      }
      else {
        e.getPresentation().setText(ActionsBundle.messagePointer("action.ToggleReadOnlyAttribute.mixed", finalRo, finalRw, finalF, finalD));
      }
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    VirtualFile[] files = getFiles(e.getDataContext());
    if (files.length > 0) {
      WriteAction.run(() -> {
        // Save all documents. We won't be able to save changes to the files that became read-only afterwards.
        FileDocumentManager.getInstance().saveAllDocuments();

        try {
          for (VirtualFile file : files) {
            ReadOnlyAttributeUtil.setReadOnlyAttribute(file, file.isWritable());
          }
        }
        catch (IOException x) {
          Notifications.Bus.notify(
            new Notification(Notifications.SYSTEM_MESSAGES_GROUP_ID, CommonBundle.getErrorTitle(), x.getMessage(), NotificationType.ERROR),
            e.getProject());
        }
      });
    }
  }

  private static VirtualFile[] getFiles(DataContext context) {
    VirtualFile[] files = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(context);
    return files == null ? VirtualFile.EMPTY_ARRAY : Stream.of(files).filter(VirtualFile::isInLocalFileSystem).toArray(VirtualFile[]::new);
  }
}