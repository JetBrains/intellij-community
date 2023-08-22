// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.fix;

import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.jdk.AutoBoxingInspection;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;


public class BoxPrimitiveInTernaryFix extends PsiUpdateModCommandQuickFix {
  private final SmartPsiElementPointer<PsiExpression> myPointer;

  private BoxPrimitiveInTernaryFix(PsiExpression expression) {
    myPointer = SmartPointerManager.createPointer(expression);
  }

  public static @Nullable BoxPrimitiveInTernaryFix makeFix(@Nullable PsiExpression npeExpression) {
    if (npeExpression == null) return null;
    PsiConditionalExpression parentConditional = getParentConditional(npeExpression);
    if (parentConditional == null) return null;
    PsiType type = Objects.requireNonNull(parentConditional.getType());
    PsiType expectedType = ExpectedTypeUtils.findExpectedType(parentConditional, false);
    if (expectedType instanceof PsiClassType && expectedType.isAssignableFrom(type)) {
      return new BoxPrimitiveInTernaryFix(npeExpression);
    }
    return null;
  }

  private static PsiConditionalExpression getParentConditional(@NotNull PsiExpression expression) {
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
    if (!(parent instanceof PsiConditionalExpression conditional)) return null;
    PsiType type = conditional.getType();
    if (type == null) return null;
    PsiExpression thenExpression = conditional.getThenExpression();
    if (thenExpression == null) return null;
    PsiType thenType = thenExpression.getType();
    if (thenType == null) return null;
    PsiExpression elseExpression = conditional.getElseExpression();
    if (elseExpression == null) return null;
    PsiType elseType = elseExpression.getType();
    if (elseType == null) return null;
    if (elseType instanceof PsiPrimitiveType && thenType instanceof PsiClassType) {
      if (PsiTreeUtil.isAncestor(thenExpression, expression, false) && thenType.isAssignableFrom(elseType)) return conditional;
    }
    else if (elseType instanceof PsiClassType && thenType instanceof PsiPrimitiveType) {
      if (PsiTreeUtil.isAncestor(elseExpression, expression, false) && elseType.isAssignableFrom(thenType)) return conditional;
    }
    return null;
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaBundle.message("intention.family.name.box.primitive.in.conditional.branch");
  }

  @Override
  protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    PsiExpression expression = myPointer.getElement();
    if (expression == null) return;
    expression = updater.getWritable(expression);
    PsiConditionalExpression conditional = getParentConditional(expression);
    if (conditional == null) return;
    PsiExpression thenExpression = conditional.getThenExpression();
    if (thenExpression != null) {
      AutoBoxingInspection.replaceWithBoxing(thenExpression);
    }
    PsiExpression elseExpression = conditional.getElseExpression();
    if (elseExpression != null) {
      AutoBoxingInspection.replaceWithBoxing(elseExpression);
    }
  }
}
