/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.util;

import com.intellij.psi.PsiElement;

/**
 * @author ven
 */
public class ConstantEvaluationOverflowException extends RuntimeException {
  private final PsiElement myOverflowingExpression;

  public ConstantEvaluationOverflowException(PsiElement overflowingExpression) {
    myOverflowingExpression = overflowingExpression;
  }

  public PsiElement getOverflowingExpression() {
    return myOverflowingExpression;
  }
}
