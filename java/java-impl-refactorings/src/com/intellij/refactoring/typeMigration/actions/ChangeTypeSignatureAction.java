// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.typeMigration.actions;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.actions.BaseJavaRefactoringAction;
import com.intellij.refactoring.typeMigration.ChangeTypeSignatureHandler;
import org.jetbrains.annotations.NotNull;

public class ChangeTypeSignatureAction extends BaseJavaRefactoringAction {
  @Override
  public boolean isAvailableInEditorOnly() {
    return false;
  }

  @Override
  public boolean isEnabledOnElements(PsiElement @NotNull [] elements) {
    if (elements.length > 1) return false;

    for (PsiElement element : elements) {
      if (!(element instanceof PsiMethod || element instanceof PsiVariable)) {
        return false;
      }
    }

    return true;
  }

  @Override
  protected boolean isAvailableOnElementInEditorAndFile(@NotNull PsiElement element, @NotNull Editor editor, @NotNull PsiFile file,
                                                        @NotNull DataContext context) {
    final int offset = TargetElementUtil.adjustOffset(file, editor.getDocument(), editor.getCaretModel().getOffset());
    final PsiElement psiElement = file.findElementAt(offset);
    final PsiReferenceParameterList referenceParameterList = PsiTreeUtil.getParentOfType(psiElement, PsiReferenceParameterList.class);
    if (referenceParameterList != null) {
      return referenceParameterList.getTypeArguments().length > 0;
    }
    if (psiElement instanceof PsiIdentifier) {
      PsiElement parent = psiElement.getParent();
      if (parent instanceof PsiVariable || parent instanceof PsiMethod method && !method.isConstructor()) {
        return true;
      }
    }
    return PsiTreeUtil.getParentOfType(psiElement, PsiTypeElement.class) != null;
  }

  @Override
  public RefactoringActionHandler getHandler(@NotNull DataContext dataContext) {
    return new ChangeTypeSignatureHandler();
  }
}
