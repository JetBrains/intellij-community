// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.actions;

import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.invertBoolean.InvertBooleanDelegate;
import com.intellij.refactoring.invertBoolean.InvertBooleanHandler;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class InvertBooleanAction extends BaseRefactoringAction {
  @Override
  protected boolean isAvailableInEditorOnly() {
    return false;
  }

  @Override
  protected boolean isEnabledOnElements(PsiElement @NotNull [] elements) {
    if (elements.length == 1 && elements[0] != null) {
      var delegate = InvertBooleanDelegate.EP_NAME.forLanguage(elements[0].getLanguage());
      return delegate != null && delegate.isVisibleOnElement(elements[0]);
    }
    return false;
  }

  @Override
  protected boolean isAvailableOnElementInEditorAndFile(final @NotNull PsiElement element,
                                                        final @NotNull Editor editor,
                                                        @NotNull PsiFile file,
                                                        @NotNull DataContext context) {
    var delegate = InvertBooleanDelegate.EP_NAME.forLanguage(element.getLanguage());
    return delegate != null && delegate.isAvailableOnElement(element);
  }

  @Override
  protected boolean isAvailableForLanguage(Language language) {
    return InvertBooleanDelegate.EP_NAME.forLanguage(language) != null;
  }

  @Override
  protected RefactoringActionHandler getHandler(@NotNull DataContext dataContext) {
    return new InvertBooleanHandler();
  }
}
