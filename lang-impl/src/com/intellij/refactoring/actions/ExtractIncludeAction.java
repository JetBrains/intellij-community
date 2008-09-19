package com.intellij.refactoring.actions;

import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.lang.LanguageExtractInclude;

/**
 * @author ven
 */
public class ExtractIncludeAction extends BaseRefactoringAction {
  public boolean isAvailableInEditorOnly() {
    return true;
  }

  public boolean isEnabledOnElements(PsiElement[] elements) {
    return false;
  }

  protected boolean isAvailableForLanguage(Language language) {
    return true;
  }

  protected boolean isAvailableForFile(PsiFile file) {
    final Language baseLanguage = file.getViewProvider().getBaseLanguage();
    return LanguageExtractInclude.INSTANCE.forLanguage(baseLanguage) != null;
  }

  public RefactoringActionHandler getHandler(DataContext dataContext) {
    PsiFile file = LangDataKeys.PSI_FILE.getData(dataContext);
    if (file == null) return null;
    return LanguageExtractInclude.INSTANCE.forLanguage(file.getViewProvider().getBaseLanguage());
  }
}
