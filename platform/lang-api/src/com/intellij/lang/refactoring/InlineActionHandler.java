// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.lang.refactoring;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions.ActionText;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;


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

  public boolean isEnabledOnElement(PsiElement element, @Nullable Editor editor) {
    return isEnabledOnElement(element);
  }

  public abstract boolean isEnabledForLanguage(Language l);

  public abstract boolean canInlineElement(PsiElement element);

  public boolean canInlineElementInEditor(PsiElement element, Editor editor) {
    return canInlineElement(element);
  }

  public abstract void inlineElement(Project project, Editor editor, PsiElement element);

  @Nullable
  @ActionText
  public String getActionName(PsiElement element) {
    return null;
  }
}
