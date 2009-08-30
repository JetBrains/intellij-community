/*
 * User: anna
 * Date: 06-May-2008
 */
package com.intellij.refactoring.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.extractMethodObject.ExtractMethodObjectHandler;

public class ReplaceMethodWithMethodObjectAction extends BaseRefactoringAction{
  protected boolean isAvailableInEditorOnly() {
    return true;
  }

  protected boolean isEnabledOnElements(final PsiElement[] elements) {
    return false;
  }

  protected RefactoringActionHandler getHandler(final DataContext dataContext) {
    return new ExtractMethodObjectHandler();
  }
}