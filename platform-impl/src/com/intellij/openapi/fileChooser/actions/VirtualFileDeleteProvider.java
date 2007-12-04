package com.intellij.openapi.fileChooser.actions;

import com.intellij.ide.DeleteProvider;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.UIBundle;

import java.io.IOException;

public final class VirtualFileDeleteProvider implements DeleteProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.fileChooser.actions.VirtualFileDeleteProvider");

  public boolean canDeleteElement(DataContext dataContext) {
    final VirtualFile[] files = PlatformDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext);
    return files != null && files.length > 0;
  }

  public void deleteElement(DataContext dataContext) {
    final VirtualFile[] files = PlatformDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext);
    if (files == null || files.length == 0) return;

    String message = createConfirmationMessage(files);
    int returnValue = Messages.showYesNoDialog(message, UIBundle.message("delete.dialog.title"), Messages.getQuestionIcon());
    if (returnValue != 0) return;

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        for (final VirtualFile file : files) {
          try {
            file.delete(this);
          }
          catch (IOException e) {
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              public void run() {
                Messages.showMessageDialog(UIBundle.message("file.chooser.could.not.erase.file.or.folder.error.messabe", file.getName()),
                                           UIBundle.message("error.dialog.title"), Messages.getErrorIcon());
              }
            });
          }
        }
      }
    }
    );
  }

  private static String createConfirmationMessage(VirtualFile[] filesToDelete) {
    if (filesToDelete.length == 1){
      if (filesToDelete[0].isDirectory()) return UIBundle.message("are.you.sure.you.want.to.delete.selected.folder.confirmation.message");
      else return UIBundle.message("are.you.sure.you.want.to.delete.selected.file.confirmation.message");
    }
    else {
      boolean hasFiles = false;
      boolean hasFolders = false;
      for (VirtualFile file : filesToDelete) {
        boolean isDirectory = file.isDirectory();
        hasFiles |= !isDirectory;
        hasFolders |= isDirectory;
      }
      LOG.assertTrue(hasFiles || hasFolders);
      if (hasFiles && hasFolders) return UIBundle
        .message("are.you.sure.you.want.to.delete.selected.files.and.directories.confirmation.message");
      else if (hasFolders) return UIBundle.message("are.you.sure.you.want.to.delete.selected.folders.confirmation.message");
      else return UIBundle.message("are.you.sure.you.want.to.delete.selected.files.and.files.confirmation.message");
    }
  }
}