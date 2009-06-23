package com.intellij.refactoring.actions;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageRefactoringSupport;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.lang.ElementsHandler;

public class PullUpAction extends BaseRefactoringAction {
  public boolean isAvailableInEditorOnly() {
    return false;
  }

  public boolean isEnabledOnElements(PsiElement[] elements) {
    if (elements.length > 0) {
      final Language language = elements[0].getLanguage();
      final RefactoringActionHandler handler = LanguageRefactoringSupport.INSTANCE.forLanguage(language).getPullUpHandler();
      return handler instanceof ElementsHandler && ((ElementsHandler)handler).isEnabledOnElements(elements);
    }
    return false;
  }

  public RefactoringActionHandler getHandler(DataContext dataContext) {
    final Language language = LangDataKeys.LANGUAGE.getData(dataContext);
    if (language != null) {
      return LanguageRefactoringSupport.INSTANCE.forLanguage(language).getPullUpHandler();
    }

    return null;
  }

  protected boolean isAvailableForLanguage(final Language language) {
    return LanguageRefactoringSupport.INSTANCE.forLanguage(language).getPullUpHandler() != null;
  }
}