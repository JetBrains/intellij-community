// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.util;

import com.intellij.psi.*;

/**
 * @author max
 */
public class EnclosingLoopOrSwitchMatcherExpression extends EnclosingLoopMatcherExpression {
  public static final PsiMatcherExpression INSTANCE = new EnclosingLoopOrSwitchMatcherExpression();

  @Override
  public Boolean match(PsiElement element) {
    if (element instanceof PsiSwitchStatement) return Boolean.TRUE;
    return super.match(element);
  }
}