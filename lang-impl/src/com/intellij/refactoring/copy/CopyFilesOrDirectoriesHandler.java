package com.intellij.refactoring.copy;

import com.intellij.ide.util.EditorHelper;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.HashSet;

public class CopyFilesOrDirectoriesHandler implements CopyHandlerDelegate {
  public boolean canCopy(final PsiElement[] elements) {
    return canCopyFiles(elements) || canCopyDirectories(elements);
  }

  protected boolean canCopyFiles(PsiElement[] elements) {
    // the second 'for' statement is for effectivity - to prevent creation of the 'names' array
    HashSet<String> names = new HashSet<String>();
    for (PsiElement element : elements) {
      if (!(element instanceof PsiFile)) {
        return false;
      }
      PsiFile file = (PsiFile)element;
      String name = file.getName();
      if (names.contains(name)) {
        return false;
      }

      names.add(name);
    }

    return true;
  }

  protected boolean canCopyDirectories(PsiElement[] elements) {
    for (PsiElement element : elements) {
      if (!(element instanceof PsiDirectory)) {
        return false;
      }
    }

    PsiElement[] filteredElements = PsiTreeUtil.filterAncestors(elements);
    return filteredElements.length == elements.length;

  }

  public void doCopy(final PsiElement[] elements, PsiDirectory defaultTargetDirectory) {
    if (defaultTargetDirectory == null) {
      defaultTargetDirectory = getCommonParentDirectory(elements);
    }

    Project project = elements [0].getProject();
    CopyFilesOrDirectoriesDialog dialog = new CopyFilesOrDirectoriesDialog(elements, defaultTargetDirectory, project, false);
    dialog.show();
    if (dialog.isOK()) {
      String newName = elements.length == 1 ? dialog.getNewName() : null;
      copyImpl(elements, newName, dialog.getTargetDirectory(), false);
    }
  }

  public void doClone(final PsiElement element) {
    PsiDirectory targetDirectory;
    if (element instanceof PsiDirectory) {
      targetDirectory = ((PsiDirectory)element).getParentDirectory();
    }
    else  {
      targetDirectory = ((PsiFile)element).getContainingDirectory();
    }

    PsiElement[] elements = new PsiElement[]{element};
    CopyFilesOrDirectoriesDialog dialog = new CopyFilesOrDirectoriesDialog(elements, null, element.getProject(), true);
    dialog.show();
    if (dialog.isOK()) {
      String newName = dialog.getNewName();
      copyImpl(elements, newName, targetDirectory, true);
    }
  }

  @Nullable
  private static PsiDirectory getCommonParentDirectory(PsiElement[] elements){
    PsiDirectory result = null;

    for (PsiElement element : elements) {
      PsiDirectory directory;

      if (element instanceof PsiDirectory) {
        directory = (PsiDirectory)element;
        directory = directory.getParentDirectory();
      }
      else if (element instanceof PsiFile) {
        directory = ((PsiFile)element).getContainingDirectory();
      }
      else {
        throw new IllegalArgumentException("unexpected element " + element);
      }

      if (directory == null) continue;

      if (result == null) {
        result = directory;
      }
      else {
        if (PsiTreeUtil.isAncestor(directory, result, true)) {
          result = directory;
        }
      }
    }

    return result;
  }

  /**
   *
   * @param elements
   * @param newName can be not null only if elements.length == 1
   * @param targetDirectory
   */
  private static void copyImpl(final PsiElement[] elements, final String newName, final PsiDirectory targetDirectory, final boolean doClone) {
    if (doClone && elements.length != 1) {
      throw new IllegalArgumentException("invalid number of elements to clone:" + elements.length);
    }

    if (newName != null && elements.length != 1) {
      throw new IllegalArgumentException("no new name should be set; number of elements is: " + elements.length);
    }

    final Project project = targetDirectory.getProject();
    Runnable command = new Runnable() {
      public void run() {
        final Runnable action = new Runnable() {
          public void run() {
            try {
              PsiFile firstFile = null;

              for (PsiElement element : elements) {
                PsiFile f = copyToDirectory((PsiFileSystemItem)element, newName, targetDirectory);
                if (firstFile == null) {
                  firstFile = f;
                }
              }

              if (firstFile != null) {
                CopyHandler.updateSelectionInActiveProjectView(firstFile, project, doClone);
                if (!(firstFile instanceof PsiBinaryFile)){
                  EditorHelper.openInEditor(firstFile);
                  ApplicationManager.getApplication().invokeLater(new Runnable() {
                                  public void run() {
                                    ToolWindowManager.getInstance(project).activateEditorComponent();
                                  }
                                });
                }
              }
            }
            catch (final IncorrectOperationException ex) {
              ApplicationManager.getApplication().invokeLater(new Runnable() {
                public void run() {
                  Messages.showMessageDialog(project, ex.getMessage(), RefactoringBundle.message("error.title"), Messages.getErrorIcon());
                }
              });
            }
            catch (final IOException ex) {
              ApplicationManager.getApplication().invokeLater(new Runnable() {
                public void run() {
                  Messages.showMessageDialog(project, ex.getMessage(), RefactoringBundle.message("error.title"), Messages.getErrorIcon());
                }
              });
            }
          }
        };
        ApplicationManager.getApplication().runWriteAction(action);
      }
    };
    CommandProcessor.getInstance().executeCommand(project, command, doClone ?
                                                                    RefactoringBundle.message("copy,handler.clone.files.directories") :
                                                                    RefactoringBundle.message("copy.handler.copy.files.directories"), null);
  }

  /**
   * @param elementToCopy PsiFile or PsiDirectory
   * @param newName can be not null only if elements.length == 1
   * @return first copied PsiFile (recursivly); null if no PsiFiles copied
   */
  @Nullable
  public static PsiFile copyToDirectory(@NotNull PsiFileSystemItem elementToCopy, @Nullable String newName, @NotNull PsiDirectory targetDirectory)
    throws IncorrectOperationException, IOException {
    if (elementToCopy instanceof PsiFile) {
      PsiFile file = (PsiFile)elementToCopy;
      String name = newName == null ? file.getName() : newName;
      return targetDirectory.copyFileFrom(name, file);
    }
    else if (elementToCopy instanceof PsiDirectory) {
      PsiDirectory directory = (PsiDirectory)elementToCopy;
      if (directory.equals(targetDirectory)) {
        return null;
      }
      if (newName == null) newName = directory.getName();
      final PsiDirectory subdirectory = targetDirectory.createSubdirectory(newName);
      VfsUtil.doActionAndRestoreEncoding(directory.getVirtualFile(), new ThrowableComputable<VirtualFile, IOException>() {
        public VirtualFile compute() {
          return subdirectory.getVirtualFile();
        }
      });

      PsiFile firstFile = null;
      PsiElement[] children = directory.getChildren();
      for (PsiElement child : children) {
        PsiFileSystemItem item = (PsiFileSystemItem)child;
        PsiFile f = copyToDirectory(item, item.getName(), subdirectory);
        if (firstFile == null) {
          firstFile = f;
        }
      }
      return firstFile;
    }
    else {
      throw new IllegalArgumentException("unexpected elementToCopy: " + elementToCopy);
    }
  }
}
