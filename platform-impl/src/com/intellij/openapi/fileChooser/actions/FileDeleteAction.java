package com.intellij.openapi.fileChooser.actions;

import com.intellij.ide.DeleteProvider;
import com.intellij.ide.actions.DeleteAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.fileChooser.ex.FileChooserDialogImpl;
import com.intellij.openapi.fileChooser.ex.FileSystemTreeImpl;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.UIBundle;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class FileDeleteAction extends DeleteAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.chooser.FileDeleteAction");

  public FileDeleteAction() {
    setEnabledInModalContext(true);
  }

  protected DeleteProvider getDeleteProvider(DataContext dataContext) {
    final FileSystemTreeImpl fileSystemTree = FileChooserDialogImpl.FILE_SYSTEM_TREE.getData(dataContext);
    if (fileSystemTree != null) {
      return new FileSystemDeleteProvider(fileSystemTree);
    }
    return null;
  }

  private static final class FileSystemDeleteProvider implements DeleteProvider {
    private final FileSystemTree myTree;

    public FileSystemDeleteProvider(@NotNull FileSystemTree tree) {
      myTree = tree;
    }

    public boolean canDeleteElement(DataContext dataContext) {return myTree.selectionExists(); }

    public void deleteElement(DataContext dataContext) { deleteSelectedFiles(); }

    void deleteSelectedFiles() {
      final VirtualFile[] files = myTree.getSelectedFiles();
      if (files.length == 0) return;

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
}
