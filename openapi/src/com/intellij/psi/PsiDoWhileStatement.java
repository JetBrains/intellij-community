/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

/**
 *
 */
public interface PsiDoWhileStatement extends PsiStatement {
  PsiExpression getCondition();
  PsiStatement getBody();

  PsiKeyword getWhileKeyword();

  PsiJavaToken getLParenth();

  PsiJavaToken getRParenth();
}
