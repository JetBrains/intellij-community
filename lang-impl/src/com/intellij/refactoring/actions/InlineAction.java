/**
 * class InlineAction
 * created Aug 28, 2001
 * @author Jeka
 */
package com.intellij.refactoring.actions;

import com.intellij.lang.Language;
import com.intellij.lang.refactoring.InlineActionHandler;
import com.intellij.lang.refactoring.InlineHandlers;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.inline.InlineRefactoringActionHandler;

public class InlineAction extends BaseRefactoringAction {

  public boolean isAvailableInEditorOnly() {
    return false;
  }

  public boolean isEnabledOnElements(PsiElement[] elements) {
    return elements.length == 1 && hasInlineActionHandler(elements [0]);
  }

  private boolean hasInlineActionHandler(PsiElement element) {
    for(InlineActionHandler handler: Extensions.getExtensions(InlineActionHandler.EP_NAME)) {
      if (handler.isEnabledOnElement(element)) {
        return true;
      }
    }
    return false;
  }

  public RefactoringActionHandler getHandler(DataContext dataContext) {
    return new InlineRefactoringActionHandler();
  }

  protected boolean isAvailableForLanguage(Language language) {
    for(InlineActionHandler handler: Extensions.getExtensions(InlineActionHandler.EP_NAME)) {
      if (handler.isEnabledForLanguage(language)) {
        return true;
      }
    }
    return InlineHandlers.getInlineHandlers(language).size() > 0;
  }
}
