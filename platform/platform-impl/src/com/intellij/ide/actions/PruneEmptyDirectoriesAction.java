/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
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
  public void update(AnActionEvent e) {
    VirtualFile[] files = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(e.getDataContext());
    e.getPresentation().setEnabled(files != null && files.length > 0);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    VirtualFile[] files = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(e.getDataContext());
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
    VfsUtilCore.visitChildrenRecursively(file, new VirtualFileVisitor() {
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
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        try {
          file.delete(PruneEmptyDirectoriesAction.class);
          //noinspection UseOfSystemOutOrSystemErr
          System.out.println("Deleted: " + file.getPresentableUrl());
        }
        catch (IOException e) {
          Messages.showErrorDialog("Cannot delete '" + file.getPresentableUrl() + "', " + e.getLocalizedMessage(), "IOException");
        }
      }
    });
  }
}
