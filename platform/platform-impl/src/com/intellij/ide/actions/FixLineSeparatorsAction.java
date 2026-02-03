// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;


@ApiStatus.Internal
public final class FixLineSeparatorsAction extends AnAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    final VirtualFile[] vFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    if (project == null || vFiles == null) return;
    CommandProcessor.getInstance().executeCommand(project, () -> {
      for (VirtualFile vFile : vFiles) {
        fixSeparators(vFile);
      }
    }, IdeBundle.message("command.fixing.line.separators"), null);
  }

  private static void fixSeparators(@NotNull VirtualFile vFile) {
    VfsUtilCore.visitChildrenRecursively(vFile, new VirtualFileVisitor<Void>() {
      @Override
      public boolean visitFile(@NotNull VirtualFile file) {
        if (!file.isDirectory() && !file.getFileType().isBinary()) {
          final Document document = FileDocumentManager.getInstance().getDocument(file);
          if (document != null && areSeparatorsBroken(document)) {
            fixSeparators(document);
          }
        }
        return true;
      }
    });
  }

  private static boolean areSeparatorsBroken(@NotNull Document document) {
    final int count = document.getLineCount();
    for (int i = 1; i < count; i += 2) {
      if (document.getLineStartOffset(i) != document.getLineEndOffset(i)) {
        return false;
      }
    }
    return true;    
  }

  private static void fixSeparators(@NotNull Document document) {
    ApplicationManager.getApplication().runWriteAction(() -> {
      int i = 1;
      while(i < document.getLineCount()) {
        final int start = document.getLineEndOffset(i);
        final int end = document.getLineEndOffset(i) + document.getLineSeparatorLength(i);
        document.deleteString(start, end);
        i++;
      }
    });
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
