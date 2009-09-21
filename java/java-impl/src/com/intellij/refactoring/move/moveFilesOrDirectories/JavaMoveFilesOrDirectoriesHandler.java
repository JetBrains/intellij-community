package com.intellij.refactoring.move.moveFilesOrDirectories;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.copy.JavaCopyFilesOrDirectoriesHandler;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.codeInsight.daemon.impl.CollectHighlightsUtil;

public class JavaMoveFilesOrDirectoriesHandler extends MoveFilesOrDirectoriesHandler {
  @Override
  public boolean canMove(PsiElement[] elements, PsiElement targetContainer) {
    final PsiElement[] srcElements = adjustForMove(null, elements, targetContainer);
    assert srcElements != null;

    boolean allJava = true;
    for (PsiElement element : srcElements) {
      if (element instanceof PsiDirectory) {
        allJava &= JavaCopyFilesOrDirectoriesHandler.hasPackages((PsiDirectory)element);
      }
      else if (element instanceof PsiFile) {
        allJava &= element instanceof PsiJavaFile && !JspPsiUtil.isInJspFile(element) && !CollectHighlightsUtil.isOutsideSourceRootJavaFile((PsiJavaFile) element);
      }
      else {
        return false;
      }
    }
    if (allJava) return false;

    return super.canMove(srcElements, targetContainer);
  }

  @Override
  public PsiElement[] adjustForMove(Project project, PsiElement[] sourceElements, PsiElement targetElement) {
    PsiElement[] result = new PsiElement[sourceElements.length];
    for(int i = 0; i < sourceElements.length; i++) {
      result[i] = sourceElements[i] instanceof PsiClass ? sourceElements[i].getContainingFile() : sourceElements[i];
    }
    return result;
  }

  @Override
  public void doMove(Project project, PsiElement[] elements, PsiElement targetContainer, MoveCallback callback) {
    super.doMove(project, elements, targetContainer, callback);    //To change body of overridden methods use File | Settings | File Templates.
  }
}
