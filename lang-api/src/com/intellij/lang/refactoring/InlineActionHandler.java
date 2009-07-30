package com.intellij.lang.refactoring;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;

/**
 * @author yole
 */
public abstract class InlineActionHandler {
  public static final ExtensionPointName<InlineActionHandler> EP_NAME = ExtensionPointName.create("com.intellij.inlineActionHandler");

  /**
   * Fast check to see if the handler can possibly inline the element. Called from action update.
   *
   * @param element the element under caret
   * @return true if the handler can possibly inline the element (with some additional conditions), false otherwise.
   */
  public boolean isEnabledOnElement(PsiElement element) {
    return canInlineElement(element);
  }

  public abstract boolean isEnabledForLanguage(Language l);

  public abstract boolean canInlineElement(PsiElement element);

  public boolean canInlineElementInEditor(PsiElement element) {
    return canInlineElement(element);
  }

  public abstract void inlineElement(Project project, Editor editor, PsiElement element);
}
