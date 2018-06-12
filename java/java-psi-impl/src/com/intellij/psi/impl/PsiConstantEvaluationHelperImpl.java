/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
    assert expressionEvaluator != null;

    return expressionEvaluator.computeConstantExpression(expression, throwExceptionOnOverflow);
  }

  @Override
  public Object computeExpression(@NotNull final PsiExpression expression, final boolean throwExceptionOnOverflow, @Nullable final AuxEvaluator auxEvaluator) {
    ConstantExpressionEvaluator expressionEvaluator = LanguageConstantExpressionEvaluator.INSTANCE.forLanguage(expression.getLanguage());
    assert expressionEvaluator != null;
    return expressionEvaluator.computeExpression(expression, throwExceptionOnOverflow, auxEvaluator);
  }

  public static Object computeCastTo(@NotNull PsiExpression expression, @NotNull PsiType castTo, @Nullable Set<PsiVariable> visitedVars) {
    Object value = JavaConstantExpressionEvaluator.computeConstantExpression(expression, visitedVars, false);
    if (value == null) return null;
    return ConstantExpressionUtil.computeCastTo(value, castTo);
  }
}
