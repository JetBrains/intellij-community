// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl;

import com.intellij.psi.*;
import com.intellij.psi.util.ConstantExpressionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * @author ven
 */
public class PsiConstantEvaluationHelperImpl extends PsiConstantEvaluationHelper {

  @Override
  public Object computeConstantExpression(@Nullable PsiElement expression) {
    return computeConstantExpression(expression, false);
  }

  @Override
  public Object computeConstantExpression(@Nullable PsiElement expression, boolean throwExceptionOnOverflow) {
    if (expression == null) return null;
    ConstantExpressionEvaluator expressionEvaluator = LanguageConstantExpressionEvaluator.INSTANCE.forLanguage(expression.getLanguage());

    if (expressionEvaluator != null) {
      return expressionEvaluator.computeConstantExpression(expression, throwExceptionOnOverflow);
    }
    return null;
  }

  @Override
  public Object computeExpression(@NotNull final PsiExpression expression, final boolean throwExceptionOnOverflow, @Nullable final AuxEvaluator auxEvaluator) {
    ConstantExpressionEvaluator expressionEvaluator = LanguageConstantExpressionEvaluator.INSTANCE.forLanguage(expression.getLanguage());
    if (expressionEvaluator != null) {
      return expressionEvaluator.computeExpression(expression, throwExceptionOnOverflow, auxEvaluator);
    }
    return null;
  }

  public static Object computeCastTo(@NotNull PsiExpression expression, @NotNull PsiType castTo, @Nullable Set<PsiVariable> visitedVars) {
    Object value = JavaConstantExpressionEvaluator.computeConstantExpression(expression, visitedVars, false);
    if (value == null) return null;
    return ConstantExpressionUtil.computeCastTo(value, castTo);
  }
}
