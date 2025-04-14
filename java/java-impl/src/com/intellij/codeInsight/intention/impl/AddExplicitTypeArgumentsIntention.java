// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.daemon.impl.quickfix.AddTypeArgumentsFix;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class AddExplicitTypeArgumentsIntention extends PsiUpdateModCommandAction<PsiIdentifier> {
  public AddExplicitTypeArgumentsIntention() {
    super(PsiIdentifier.class);
  }
  
  @Override
  public @NotNull String getFamilyName() {
    return JavaBundle.message("intention.add.explicit.type.arguments.family");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiIdentifier identifier) {
    PsiReferenceExpression methodExpression = ObjectUtils.tryCast(identifier.getParent(), PsiReferenceExpression.class);
    if (methodExpression == null) return null;
    PsiElement parent = methodExpression.getParent();
    if (parent instanceof PsiMethodCallExpression callExpression && callExpression.getTypeArguments().length == 0) {
      JavaResolveResult result = callExpression.resolveMethodGenerics();
      if (result instanceof MethodCandidateInfo candidateInfo && candidateInfo.isApplicable()) {
        PsiMethod method = candidateInfo.getElement();
        if (!method.isConstructor() && method.hasTypeParameters() && AddTypeArgumentsFix.addTypeArguments(callExpression, null) != null) {
          return Presentation.of(getFamilyName());
        }
      }
    }
    return null;
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiIdentifier element, @NotNull ModPsiUpdater updater) {
    PsiMethodCallExpression callExpression = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
    assert callExpression != null;
    PsiExpression withArgs = AddTypeArgumentsFix.addTypeArguments(callExpression, null);
    if (withArgs != null) {
      CodeStyleManager.getInstance(context.project()).reformat(callExpression.replace(withArgs));
    }
  }
}