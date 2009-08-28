/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 06.05.2002
 * Time: 14:03:43
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageRefactoringSupport;

public class IntroduceParameterAction extends BaseRefactoringAction {
  protected boolean isAvailableInEditorOnly() {
    return true;
  }

  protected boolean isEnabledOnElements(PsiElement[] elements) {
    return false;
  }

  protected RefactoringActionHandler getHandler(DataContext dataContext) {
    final Language language = LangDataKeys.LANGUAGE.getData(dataContext);
    if (language != null) {
      return LanguageRefactoringSupport.INSTANCE.forLanguage(language).getIntroduceParameterHandler();
    }

    return null;
  }

  protected boolean isAvailableForLanguage(Language language) {
    return LanguageRefactoringSupport.INSTANCE.forLanguage(language).getIntroduceParameterHandler() != null;
  }
}
