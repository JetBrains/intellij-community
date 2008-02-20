package com.intellij.refactoring.move.moveFilesOrDirectories;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.move.MoveHandlerDelegate;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;

public class MoveFilesOrDirectoriesHandler extends MoveHandlerDelegate {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesHandler");

  public boolean canMove(final PsiElement[] elements, final PsiElement targetContainer) {
    if (!canMoveFiles(elements) && !canMoveDirectories(elements)) return false;
    return super.canMove(elements, targetContainer);
  }

  public boolean isValidTarget(final PsiElement psiElement) {
    return psiElement instanceof PsiDirectory || psiElement instanceof PsiDirectoryContainer;
  }

  public void doMove(final Project project, final PsiElement[] elements, final PsiElement targetContainer, final MoveCallback callback) {
    if (!LOG.assertTrue(targetContainer == null || targetContainer instanceof PsiDirectory || targetContainer instanceof PsiDirectoryContainer)) {
      return;
    }
    MoveFilesOrDirectoriesUtil.doMove(project, elements, targetContainer, callback);
  }

  public boolean tryToMove(final PsiElement element, final Project project, final DataContext dataContext, final PsiReference reference) {
    if ((element instanceof PsiFile && ((PsiFile)element).getVirtualFile() != null)
        || element instanceof PsiDirectory) {
      final PsiElement targetContainer = (PsiElement)dataContext.getData(DataConstantsEx.TARGET_PSI_ELEMENT);
      MoveFilesOrDirectoriesUtil.doMove(project, new PsiElement[]{element}, targetContainer, null);
      return true;
    }
    if (element instanceof PsiPlainText) {
      PsiFile file = element.getContainingFile();
      PsiElement[] elements = new PsiElement[]{file};
      if (canMoveFiles(elements)) {
        doMove(project, elements, null, null);
      }
      return true;
    }
    return false;
  }

  protected boolean canMoveFiles(@NotNull PsiElement[] elements) {
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

  protected boolean canMoveDirectories(@NotNull PsiElement[] elements) {
    for (PsiElement element : elements) {
      if (!(element instanceof PsiDirectory)) {
        return false;
      }
    }

    PsiElement[] filteredElements = PsiTreeUtil.filterAncestors(elements);
    if (filteredElements.length != elements.length) {
      // there are nested dirs
      return false;
    }

    return true;
  }
}
