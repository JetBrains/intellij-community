// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.daemon.impl.quickfix.AddTypeArgumentsFix;
import com.intellij.codeInsight.intention.BaseElementAtCaretIntentionAction;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

public final class AddExplicitTypeArgumentsIntention extends BaseElementAtCaretIntentionAction {
  @Override
  @NotNull
  public String getFamilyName() {
    return JavaBundle.message("intention.add.explicit.type.arguments.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, @NotNull Editor editor, @NotNull PsiElement element) {
    PsiIdentifier identifier = ObjectUtils.tryCast(element, PsiIdentifier.class);
    if (identifier == null) return false;
    PsiReferenceExpression methodExpression = ObjectUtils.tryCast(identifier.getParent(), PsiReferenceExpression.class);
    if (methodExpression == null) return false;
    PsiElement parent = methodExpression.getParent();
    if (parent instanceof PsiMethodCallExpression callExpression && callExpression.getTypeArguments().length == 0) {
      JavaResolveResult result = callExpression.resolveMethodGenerics();
      if (result instanceof MethodCandidateInfo candidateInfo && candidateInfo.isApplicable()) {
        PsiMethod method = candidateInfo.getElement();
        setText(getFamilyName());
        return !method.isConstructor() && method.hasTypeParameters() && AddTypeArgumentsFix.addTypeArguments(callExpression, null) != null;
      }
    }
    return false;
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    PsiMethodCallExpression callExpression = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
    assert callExpression != null;
    PsiExpression withArgs = AddTypeArgumentsFix.addTypeArguments(callExpression, null);
    if (withArgs != null) {
      CodeStyleManager.getInstance(project).reformat(callExpression.replace(withArgs));
    }
  }
}