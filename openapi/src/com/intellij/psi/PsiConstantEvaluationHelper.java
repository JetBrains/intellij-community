/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

/**
 * @author ven
 */
public abstract class PsiConstantEvaluationHelper {
  public Object computeConstantExpression(PsiExpression expression) {
    return computeConstantExpression(expression, false);
  }

  public abstract Object computeConstantExpression(PsiExpression expression, boolean throwExceptionOnOverflow);
}
