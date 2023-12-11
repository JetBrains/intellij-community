// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.daemon.impl.quickfix.SimplifyBooleanExpressionFix;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.*;
import com.intellij.psi.impl.JavaConstantExpressionEvaluator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SimplifyBooleanExpressionAction extends PsiUpdateModCommandAction<PsiExpression> {
  public SimplifyBooleanExpressionAction() {
    super(PsiExpression.class);
  }
  
  @Override
  @NotNull
  public String getFamilyName() {
    return SimplifyBooleanExpressionFix.getFamilyNameText();
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiExpression element) {
    PsiExpression expression = getExpressionToSimplify(element);
    if (!SimplifyBooleanExpressionFix.canBeSimplified(expression)) return null;
    Object o = JavaConstantExpressionEvaluator.computeConstantExpression(expression, false);
    return Presentation.of(o instanceof Boolean ? SimplifyBooleanExpressionFix.getIntentionText(expression, (Boolean)o) : getFamilyName());
  }

  private static @NotNull PsiExpression getExpressionToSimplify(@NotNull PsiExpression element) {
    PsiExpression expression = element;
    PsiElement parent = expression;
    while (parent instanceof PsiExpression parentExpression &&
           !(parent instanceof PsiAssignmentExpression) &&
           (PsiTypes.booleanType().equals(parentExpression.getType()) || parent instanceof PsiConditionalExpression)) {
      expression = parentExpression;
      parent = parent.getParent();
    }
    return expression;
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiExpression element, @NotNull ModPsiUpdater updater) {
    PsiExpression expression = getExpressionToSimplify(element);
    SimplifyBooleanExpressionFix.simplifyExpression(expression);
  }
}