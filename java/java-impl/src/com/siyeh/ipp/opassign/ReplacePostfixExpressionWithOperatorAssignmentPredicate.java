// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.opassign;

import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPostfixExpression;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ipp.base.PsiElementPredicate;

class ReplacePostfixExpressionWithOperatorAssignmentPredicate implements PsiElementPredicate {

  @Override
  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiPostfixExpression postfixExpression)) {
      return false;
    }
    final IElementType tokenType = postfixExpression.getOperationTokenType();
    return !(!JavaTokenType.PLUSPLUS.equals(tokenType) && !JavaTokenType.MINUSMINUS.equals(tokenType));
  }
}