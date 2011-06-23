package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFileHandler;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesUtil;
import com.intellij.usageView.UsageInfo;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author ksafonov
 */
public abstract class MoveDirectoryWithClassesHelper {
  private static final ExtensionPointName<MoveDirectoryWithClassesHelper> EP_NAME =
    ExtensionPointName.create("com.intellij.refactoring.moveDirectoryWithClassesHelper");

  public abstract void findUsages(Collection<PsiFile> filesToMove, PsiDirectory[] directoriesToMove, Collection<UsageInfo> result,
                                  boolean searchInComments, boolean searchInNonJavaFiles, Project project);

  public abstract boolean move(PsiFile file,
                                  PsiDirectory moveDestination,
                                  Map<PsiElement, PsiElement> oldToNewElementsMapping,
                                  List<PsiFile> movedFiles,
                                  RefactoringElementListener listener);

  public abstract void postProcessUsages(UsageInfo[] usages);

  public abstract void beforeMove(PsiFile psiFile);

  public abstract void afterMove(PsiElement newElement);

  public static MoveDirectoryWithClassesHelper[] findAll() {
    return Extensions.getExtensions(EP_NAME);
  }


  public static class Default extends MoveDirectoryWithClassesHelper {

    @Override
    public void findUsages(Collection<PsiFile> filesToMove, PsiDirectory[] directoriesToMove, Collection<UsageInfo> result,
                           boolean searchInComments, boolean searchInNonJavaFiles, Project project) {
    }

    @Override
    public boolean move(PsiFile psiFile,
                           PsiDirectory moveDestination,
                           Map<PsiElement, PsiElement> oldToNewElementsMapping,
                           List<PsiFile> movedFiles,
                           RefactoringElementListener listener) {
      if (moveDestination.equals(psiFile.getContainingDirectory())) {
        return false;
      }

      MoveFileHandler.forElement(psiFile).prepareMovedFile(psiFile, moveDestination, oldToNewElementsMapping);

      PsiFile moving = moveDestination.findFile(psiFile.getName());
      if (moving == null) {
        MoveFilesOrDirectoriesUtil.doMoveFile(psiFile, moveDestination);
      }
      moving = moveDestination.findFile(psiFile.getName());
      movedFiles.add(moving);
      listener.elementMoved(psiFile);
      return true;
    }

    @Override
    public void postProcessUsages(UsageInfo[] usages) {
    }

    @Override
    public void beforeMove(PsiFile psiFile) {
    }

    @Override
    public void afterMove(PsiElement newElement) {
    }
  }
}
