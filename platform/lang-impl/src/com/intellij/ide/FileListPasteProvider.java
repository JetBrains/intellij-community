package com.intellij.ide;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiManager;
import com.intellij.refactoring.copy.CopyFilesOrDirectoriesHandler;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class FileListPasteProvider implements PasteProvider {
  public void performPaste(DataContext dataContext) {
    Project project = LangDataKeys.PROJECT.getData(dataContext);
    final IdeView ideView = LangDataKeys.IDE_VIEW.getData(dataContext);
    if (project == null || ideView == null) return;
    List<File> fileList;
    try {
      final Transferable contents = CopyPasteManager.getInstance().getContents();
      //noinspection unchecked
      fileList = (List<File>)contents.getTransferData(DataFlavor.javaFileListFlavor);
    }
    catch (UnsupportedFlavorException e) {
      return;
    }
    catch (IOException e) {
      return;
    }
    List<PsiElement> elements = new ArrayList<PsiElement>();
    for (File file : fileList) {
      final VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
      if (vFile != null) {
        final PsiManager instance = PsiManager.getInstance(project);
        PsiFileSystemItem item = vFile.isDirectory() ? instance.findDirectory(vFile) : instance.findFile(vFile);
        if (item != null) {
          elements.add(item);
        }
      }
    }
    if (elements.size() > 0) {
      final PsiDirectory dir = ideView.getOrChooseDirectory();
      if (dir != null) {
        new CopyFilesOrDirectoriesHandler().doCopy(elements.toArray(new PsiElement[elements.size()]), dir);
      }
    }
  }

  public boolean isPastePossible(DataContext dataContext) {
    return true;
  }

  public boolean isPasteEnabled(DataContext dataContext) {
    final Transferable contents = CopyPasteManager.getInstance().getContents();
    final IdeView ideView = LangDataKeys.IDE_VIEW.getData(dataContext);
    return contents.isDataFlavorSupported(DataFlavor.javaFileListFlavor) && ideView != null;
  }
}
