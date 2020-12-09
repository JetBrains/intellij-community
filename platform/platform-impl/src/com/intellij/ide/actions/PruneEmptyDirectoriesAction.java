// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class PruneEmptyDirectoriesAction extends AnAction {
  @Override
  public void update(@NotNull AnActionEvent e) {
    VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    e.getPresentation().setEnabled(files != null && files.length > 0);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    assert files != null;

    FileTypeManager ftManager = FileTypeManager.getInstance();
    try {
      for (VirtualFile file : files) {
        pruneEmptiesIn(file, ftManager);
      }
    }
    catch (IOException ignored) { }
  }

  private static void pruneEmptiesIn(VirtualFile file, final FileTypeManager ftManager) throws IOException {
    VfsUtilCore.visitChildrenRecursively(file, new VirtualFileVisitor<Void>() {
      @Override
      public boolean visitFile(@NotNull VirtualFile file) {
        if (file.isDirectory()) {
          if (ftManager.isFileIgnored(file)) {
            return false;
          }
        }
        else {
          if (".DS_Store".equals(file.getName())) {
            delete(file);
            return false;
          }
        }
        return true;
      }

      @Override
      public void afterChildrenVisited(@NotNull VirtualFile file) {
        if (file.isDirectory() && file.getChildren().length == 0) {
          delete(file);
        }
      }
    });
  }

  private static void delete(final VirtualFile file) {
    ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        file.delete(PruneEmptyDirectoriesAction.class);
        //noinspection UseOfSystemOutOrSystemErr
        System.out.println("Deleted: " + file.getPresentableUrl());
      }
      catch (IOException e) {
        //noinspection HardCodedStringLiteral
        Messages.showErrorDialog(IdeBundle.message("message.cannot.delete.0.1", file.getPresentableUrl(), e.getLocalizedMessage()), "IOException");
      }
    });
  }
}
