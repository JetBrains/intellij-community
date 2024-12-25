// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.anonymousToInner.AnonymousToInnerHandler;
import org.jetbrains.annotations.NotNull;

public class AnonymousToInnerAction extends BaseJavaRefactoringAction {
  @Override
  public boolean isAvailableInEditorOnly() {
    return true;
  }

  @Override
  public boolean isEnabledOnElements(PsiElement @NotNull [] elements) {
    return false;
  }

  @Override
  protected boolean isAvailableOnElementInEditorAndFile(final @NotNull PsiElement element, final @NotNull Editor editor, @NotNull PsiFile file, @NotNull DataContext context) {
    final PsiElement targetElement = file.findElementAt(editor.getCaretModel().getOffset());
    if (PsiTreeUtil.getParentOfType(targetElement, PsiAnonymousClass.class) != null) {
      return true;
    }
    if (PsiTreeUtil.getParentOfType(element, PsiAnonymousClass.class) != null) {
      return true;
    }
    final PsiNewExpression newExpression = PsiTreeUtil.getParentOfType(element, PsiNewExpression.class);
    return newExpression != null && newExpression.getAnonymousClass() != null;
  }

  @Override
  public RefactoringActionHandler getHandler(@NotNull DataContext dataContext) {
    return new AnonymousToInnerHandler();
  }
}
