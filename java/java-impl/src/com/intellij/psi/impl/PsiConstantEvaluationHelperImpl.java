package com.intellij.psi.impl;

import com.intellij.psi.*;
import com.intellij.psi.util.ConstantExpressionUtil;

import java.util.Set;

/**
 * @author ven
 */
public class PsiConstantEvaluationHelperImpl extends PsiConstantEvaluationHelper {

  public Object computeConstantExpression(PsiElement expression) {
    return computeConstantExpression(expression, false);
  }

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
