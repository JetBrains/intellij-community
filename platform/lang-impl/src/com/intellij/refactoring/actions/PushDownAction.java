package com.intellij.refactoring.actions;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageRefactoringSupport;
import com.intellij.lang.refactoring.RefactoringSupportProvider;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.lang.ElementsHandler;


public class PushDownAction extends BaseRefactoringAction {
  public boolean isAvailableInEditorOnly() {
    return false;
  }

  public boolean isEnabledOnElements(PsiElement[] elements) {
    if (elements.length > 0) {
      final Language language = elements[0].getLanguage();
      final RefactoringActionHandler handler = LanguageRefactoringSupport.INSTANCE.forLanguage(language).getPushDownHandler();
      return handler instanceof ElementsHandler && ((ElementsHandler)handler).isEnabledOnElements(elements);
    }
    return false;
  }

  public RefactoringActionHandler getHandler(DataContext dataContext) {
    PsiFile file = LangDataKeys.PSI_FILE.getData(dataContext);
    if (file == null) return null;
    final RefactoringSupportProvider supportProvider = LanguageRefactoringSupport.INSTANCE.forLanguage(file.getViewProvider().getBaseLanguage());
    return supportProvider != null ? supportProvider.getPushDownHandler() : null;
  }

  protected boolean isAvailableForLanguage(final Language language) {
    return LanguageRefactoringSupport.INSTANCE.forLanguage(language).getPushDownHandler() != null;
  }
}