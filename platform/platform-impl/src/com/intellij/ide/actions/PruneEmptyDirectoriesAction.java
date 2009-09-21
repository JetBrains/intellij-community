/*
 * @author max
 */
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;

public class PruneEmptyDirectoriesAction extends AnAction {
  @Override
  public void update(AnActionEvent e) {
    VirtualFile[] files = PlatformDataKeys.VIRTUAL_FILE_ARRAY.getData(e.getDataContext());
    e.getPresentation().setEnabled(files != null && files.length > 0);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    VirtualFile[] files = PlatformDataKeys.VIRTUAL_FILE_ARRAY.getData(e.getDataContext());
    assert files != null;

    FileTypeManager ftManager = FileTypeManager.getInstance();
    try {
      for (VirtualFile file : files) {
        pruneEmptiesIn(file, ftManager);
      }
    }
    catch (IOException e1) {

    }
  }

  private static void pruneEmptiesIn(final VirtualFile file, FileTypeManager ftManager) throws IOException {
    if (file.isDirectory()) {
      if (ftManager.isFileIgnored(file.getName())) return;

      for (VirtualFile child : file.getChildren()) {
        pruneEmptiesIn(child, ftManager);
      }

      if (file.getChildren().length == 0) {
        delete(file);
      }
    }
    else if (".DS_Store".equals(file.getName())) {
      delete(file);
    }
  }

  private static void delete(final VirtualFile file) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        try {
          file.delete(null);
          System.out.println("Deleted: " + file.getPresentableUrl());
        }
        catch (IOException e) {
          Messages.showErrorDialog("Cannot delete '" + file.getPresentableUrl() + "', " + e.getLocalizedMessage(), "IOException");
        }
      }
    });
  }
}
