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

import java.util.Set;

/**
 * @author ven
 */
public class PsiConstantEvaluationHelperImpl extends PsiConstantEvaluationHelper {

  @Override
  public Object computeConstantExpression(PsiElement expression) {
    return computeConstantExpression(expression, false);
  }

  @Override
  public Object computeConstantExpression(PsiElement expression, boolean throwExceptionOnOverflow) {
    if (expression == null) return null;
    ConstantExpressionEvaluator expressionEvaluator = LanguageConstantExpressionEvaluator.INSTANCE.forLanguage(expression.getLanguage());
    assert expressionEvaluator != null;

    return expressionEvaluator.computeConstantExpression(expression, throwExceptionOnOverflow);
  }

  @Override
  public Object computeExpression(final PsiExpression expression, final boolean throwExceptionOnOverflow, final AuxEvaluator auxEvaluator) {
    if (expression == null) return null;
    ConstantExpressionEvaluator expressionEvaluator = LanguageConstantExpressionEvaluator.INSTANCE.forLanguage(expression.getLanguage());
    assert expressionEvaluator != null;
    return expressionEvaluator.computeExpression(expression, throwExceptionOnOverflow, auxEvaluator);
  }

  public static Object computeCastTo(PsiExpression expression, PsiType castTo, Set<PsiVariable> visitedVars) {
    Object value = JavaConstantExpressionEvaluator.computeConstantExpression(expression, visitedVars, false);
    if (value == null) return null;
    return ConstantExpressionUtil.computeCastTo(value, castTo);
  }
}
