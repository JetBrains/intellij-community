/*
 * @author max
 */
package com.intellij.psi.util;

import com.intellij.psi.*;

public class EnclosingLoopOrSwitchMatcherExpression implements PsiMatcherExpression {
  public static final PsiMatcherExpression INSTANCE = new EnclosingLoopOrSwitchMatcherExpression();

  public Boolean match(PsiElement element) {
    if (element instanceof PsiForStatement) return Boolean.TRUE;
    if (element instanceof PsiForeachStatement) return Boolean.TRUE;
    if (element instanceof PsiWhileStatement) return Boolean.TRUE;
    if (element instanceof PsiDoWhileStatement) return Boolean.TRUE;
    if (element instanceof PsiSwitchStatement) return Boolean.TRUE;
    if (element instanceof PsiMethod || element instanceof PsiClassInitializer) return null;
    return Boolean.FALSE;
  }
}