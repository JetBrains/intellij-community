/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

public interface PsiForStatement extends PsiStatement{
  PsiStatement getInitialization();
  PsiExpression getCondition();
  PsiStatement getUpdate();
  PsiStatement getBody();

  PsiJavaToken getLParenth();

  PsiJavaToken getRParenth();
}
