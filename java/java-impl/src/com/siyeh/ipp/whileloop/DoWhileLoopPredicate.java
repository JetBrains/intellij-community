// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.whileloop;

import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiDoWhileStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ipp.base.PsiElementPredicate;

class DoWhileLoopPredicate implements PsiElementPredicate {

  @Override
  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiJavaToken token)) {
      return false;
    }
    final IElementType tokenType = token.getTokenType();
    if (!JavaTokenType.DO_KEYWORD.equals(tokenType)) {
      return false;
    }
    final PsiElement parent = element.getParent();
    if (!(parent instanceof PsiDoWhileStatement doWhileStatement)) {
      return false;
    }
    return !(doWhileStatement.getCondition() == null ||
             doWhileStatement.getBody() == null);
  }
}