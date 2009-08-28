package com.intellij.refactoring.actions;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageRefactoringSupport;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringActionHandler;

/**
 *
 */
public class ExtractMethodAction extends BaseRefactoringAction {
  public ExtractMethodAction() {
    setInjectedContext(true);
  }

  public boolean isAvailableInEditorOnly() {
    return true;
  }

  public boolean isEnabledOnElements(PsiElement[] elements) {
    return false;
  }

  public RefactoringActionHandler getHandler(DataContext dataContext) {
    final Language language = LangDataKeys.LANGUAGE.getData(dataContext);
    if (language != null) {
      return LanguageRefactoringSupport.INSTANCE.forLanguage(language).getExtractMethodHandler();
    }

    return null;
  }

  protected boolean isAvailableForLanguage(final Language language) {
    return LanguageRefactoringSupport.INSTANCE.forLanguage(language).getExtractMethodHandler() != null;
  }
}
