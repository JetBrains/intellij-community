/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.util;

import com.intellij.psi.*;

public interface PsiMatcherExpression {
  Boolean match(PsiElement element);

  PsiMatcherExpression ENCLOSING_LOOP = new PsiMatcherExpression() {
    public Boolean match(PsiElement element) {
      if (element instanceof PsiForStatement) return Boolean.TRUE;
      if (element instanceof PsiForeachStatement) return Boolean.TRUE;
      if (element instanceof PsiWhileStatement) return Boolean.TRUE;
      if (element instanceof PsiDoWhileStatement) return Boolean.TRUE;
      if (element instanceof PsiMethod || element instanceof PsiClassInitializer) return null;
      return Boolean.FALSE;
    }
  };

  PsiMatcherExpression ENCLOSING_LOOP_OR_SWITCH = new PsiMatcherExpression() {
    public Boolean match(PsiElement element) {
      if (element instanceof PsiForStatement) return Boolean.TRUE;
      if (element instanceof PsiForeachStatement) return Boolean.TRUE;
      if (element instanceof PsiWhileStatement) return Boolean.TRUE;
      if (element instanceof PsiDoWhileStatement) return Boolean.TRUE;
      if (element instanceof PsiSwitchStatement) return Boolean.TRUE;
      if (element instanceof PsiMethod || element instanceof PsiClassInitializer) return null;
      return Boolean.FALSE;
    }
  };
}