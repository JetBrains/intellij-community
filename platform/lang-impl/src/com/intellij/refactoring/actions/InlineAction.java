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
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.inline.InlineRefactoringActionHandler;
import org.jetbrains.annotations.Nullable;

public class InlineAction extends BaseRefactoringAction {

  public boolean isAvailableInEditorOnly() {
    return false;
  }

  @Override
  protected boolean isAvailableOnElementInEditor(PsiElement element, Editor editor) {
    return hasInlineActionHandler(element, PsiUtilBase.getLanguageInEditor(editor, element.getProject()));
  }

  public boolean isEnabledOnElements(PsiElement[] elements) {
    return elements.length == 1 && hasInlineActionHandler(elements [0], null);
  }

  private static boolean hasInlineActionHandler(PsiElement element, @Nullable Language editorLanguage) {
    for(InlineActionHandler handler: Extensions.getExtensions(InlineActionHandler.EP_NAME)) {
      if (handler.isEnabledOnElement(element)) {
        return true;
      }
    }
    return InlineHandlers.getInlineHandlers(
      editorLanguage != null ? editorLanguage :element.getLanguage()
    ).size() > 0;
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
