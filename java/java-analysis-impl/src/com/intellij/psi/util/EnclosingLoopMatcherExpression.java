// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.util;

import com.intellij.psi.*;

/**
 * @author max
 */
public class EnclosingLoopMatcherExpression implements PsiMatcherExpression {
  public static final PsiMatcherExpression INSTANCE = new EnclosingLoopMatcherExpression();

  @Override
  public Boolean match(PsiElement element) {
    if (element instanceof PsiForStatement) return Boolean.TRUE;
    if (element instanceof PsiForeachStatement) return Boolean.TRUE;
    if (element instanceof PsiWhileStatement) return Boolean.TRUE;
    if (element instanceof PsiDoWhileStatement) return Boolean.TRUE;
    if (element instanceof PsiMethod || element instanceof PsiClassInitializer || element instanceof PsiLambdaExpression) return null;
    return Boolean.FALSE;
  }
}