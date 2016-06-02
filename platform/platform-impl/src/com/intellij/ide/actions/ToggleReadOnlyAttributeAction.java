/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.actions;

import com.intellij.CommonBundle;
import com.intellij.idea.ActionsBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.io.ReadOnlyAttributeUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.stream.Stream;

public class ToggleReadOnlyAttributeAction extends AnAction implements DumbAware {
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
      if (ro > 0 && rw > 0) {
        e.getPresentation().setText(ActionsBundle.message("action.ToggleReadOnlyAttribute.text"));
      }
      else if (f > 0 && d == 0) {
        e.getPresentation().setText(ActionsBundle.message("action.ToggleReadOnlyAttribute.files", ro, rw, f, d));
      }
      else if (f == 0 && d > 0) {
        e.getPresentation().setText(ActionsBundle.message("action.ToggleReadOnlyAttribute.dirs", ro, rw, f, d));
      }
      else {
        e.getPresentation().setText(ActionsBundle.message("action.ToggleReadOnlyAttribute.mixed", ro, rw, f, d));
      }
    }
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