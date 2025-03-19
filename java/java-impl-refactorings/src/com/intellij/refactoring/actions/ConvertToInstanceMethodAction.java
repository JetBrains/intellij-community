// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.convertToInstanceMethod.ConvertToInstanceMethodHandler;
import org.jetbrains.annotations.NotNull;

public class ConvertToInstanceMethodAction extends BaseJavaRefactoringAction {
  @Override
  protected boolean isAvailableInEditorOnly() {
    return false;
  }

  @Override
  protected boolean isEnabledOnElements(PsiElement @NotNull [] elements) {
    return elements.length == 1 && elements[0] instanceof PsiMethod;
  }

  @Override
  protected boolean isAvailableOnElementInEditorAndFile(@NotNull PsiElement element, final @NotNull Editor editor, @NotNull PsiFile file, @NotNull DataContext context) {
    if (element instanceof PsiIdentifier) element = element.getParent();
    return element instanceof PsiMethod && ((PsiMethod) element).hasModifierProperty(PsiModifier.STATIC);
  }

  @Override
  protected RefactoringActionHandler getHandler(@NotNull DataContext dataContext) {
    return new ConvertToInstanceMethodHandler();
  }
}
