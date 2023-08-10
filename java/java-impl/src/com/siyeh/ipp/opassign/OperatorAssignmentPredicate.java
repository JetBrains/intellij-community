// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.opassign;

import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.siyeh.ipp.base.PsiElementPredicate;

class OperatorAssignmentPredicate implements PsiElementPredicate {
  private static class Lazy {
    private static final TokenSet OPERATOR_ASSIGNMENT_TOKENS = TokenSet.create(
      JavaTokenType.PLUSEQ,
      JavaTokenType.MINUSEQ,
      JavaTokenType.ASTERISKEQ,
      JavaTokenType.PERCEQ,
      JavaTokenType.DIVEQ,
      JavaTokenType.ANDEQ,
      JavaTokenType.OREQ,
      JavaTokenType.XOREQ,
      JavaTokenType.LTLTEQ,
      JavaTokenType.GTGTEQ,
      JavaTokenType.GTGTGTEQ
    );
  }

  @Override
  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiAssignmentExpression assignmentExpression)) return false;
    IElementType tokenType = assignmentExpression.getOperationTokenType();
    return Lazy.OPERATOR_ASSIGNMENT_TOKENS.contains(tokenType);
  }
}