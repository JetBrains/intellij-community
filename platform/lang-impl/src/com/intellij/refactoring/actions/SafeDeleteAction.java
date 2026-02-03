// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.refactoring.actions;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.safeDelete.SafeDeleteHandler;
import com.intellij.refactoring.safeDelete.SafeDeleteProcessor;
import org.jetbrains.annotations.NotNull;

public final class SafeDeleteAction extends BaseRefactoringAction {
  public SafeDeleteAction() {
    setInjectedContext(true);
  }

  @Override
  public boolean isAvailableInEditorOnly() {
    return false;
  }

  @Override
  public boolean isEnabledOnElements(PsiElement @NotNull [] elements) {
    for (PsiElement element : elements) {
      if (!SafeDeleteProcessor.validElement(element)) return false;
    }
    return true;
  }

  @Override
  protected boolean isAvailableOnElementInEditorAndFile(final @NotNull PsiElement element, final @NotNull Editor editor, @NotNull PsiFile file, @NotNull DataContext context) {
    return SafeDeleteProcessor.validElement(element);
  }

  @Override
  protected boolean isAvailableOnElementInEditorAndFile(@NotNull PsiElement element,
                                                        @NotNull Editor editor,
                                                        @NotNull PsiFile file,
                                                        @NotNull DataContext context,
                                                        @NotNull String place) {
    if (!file.isValid()) return false;
    PsiElement targetElement = element;
    if (place.equals(ActionPlaces.REFACTORING_QUICKLIST)) {
      PsiElement caretElement = BaseRefactoringAction.getElementAtCaret(editor, file);
      if (! PsiTreeUtil.isAncestor(element, caretElement, false)) {
        targetElement = caretElement;
      }
    }
    return isAvailableOnElementInEditorAndFile(targetElement, editor, file, context);
  }

  @Override
  public RefactoringActionHandler getHandler(@NotNull DataContext dataContext) {
    return new SafeDeleteHandler();
  }

}
