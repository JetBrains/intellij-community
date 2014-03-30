/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class FixLineSeparatorsAction extends AnAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    final VirtualFile[] vFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    if (project == null || vFiles == null) return;
    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      public void run() {
        for (VirtualFile vFile : vFiles) {
          fixSeparators(vFile);
        }
      }
    }, "fixing line separators", null);
  }

  private static void fixSeparators(VirtualFile vFile) {
    VfsUtilCore.visitChildrenRecursively(vFile, new VirtualFileVisitor() {
      @Override
      public boolean visitFile(@NotNull VirtualFile file) {
        if (!file.isDirectory() && !file.getFileType().isBinary()) {
          final Document document = FileDocumentManager.getInstance().getDocument(file);
          if (areSeparatorsBroken(document)) {
            fixSeparators(document);
          }
        }
        return true;
      }
    });
  }

  private static boolean areSeparatorsBroken(Document document) {
    final int count = document.getLineCount();
    for (int i = 1; i < count; i += 2) {
      if (document.getLineStartOffset(i) != document.getLineEndOffset(i)) {
        return false;
      }
    }
    return true;    
  }

  private static void fixSeparators(final Document document) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        int i = 1;
        while(i < document.getLineCount()) {
          final int start = document.getLineEndOffset(i);
          final int end = document.getLineEndOffset(i) + document.getLineSeparatorLength(i);
          document.deleteString(start, end);
          i++;
        }
      }
    });
  }
}
