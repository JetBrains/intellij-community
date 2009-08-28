package com.intellij.refactoring.actions;

import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.SyntheticElement;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.rename.RenameHandlerRegistry;

public class RenameElementAction extends BaseRefactoringAction {

  public RenameElementAction() {
    setInjectedContext(true);
  }

  public boolean isAvailableInEditorOnly() {
    return false;
  }

  public boolean isEnabledOnElements(PsiElement[] elements) {
    if (elements.length != 1) return false;

    PsiElement element = elements[0];
    return element instanceof PsiNamedElement && !(element instanceof SyntheticElement);
  }

  public RefactoringActionHandler getHandler(DataContext dataContext) {
    return RenameHandlerRegistry.getInstance().getRenameHandler(dataContext);
  }

  protected boolean isEnabledOnDataContext(DataContext dataContext) {
    return RenameHandlerRegistry.getInstance().hasAvailableHandler(dataContext);
  }

  protected boolean isAvailableForLanguage(Language language) {
    return true;
  }
}
