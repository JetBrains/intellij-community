/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import com.intellij.util.IncorrectOperationException;

/**
 *
 */
public interface PsiIfStatement extends PsiStatement {
  PsiExpression getCondition();
  PsiStatement getThenBranch();
  PsiStatement getElseBranch();
  PsiKeyword getElseElement();
  void setElseBranch(PsiStatement statement) throws IncorrectOperationException;
  PsiJavaToken getLParenth();
  PsiJavaToken getRParenth();
}
