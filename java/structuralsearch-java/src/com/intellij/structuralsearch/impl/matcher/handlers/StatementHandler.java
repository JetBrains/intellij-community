// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher.handlers;

import com.intellij.psi.*;
import com.intellij.structuralsearch.impl.matcher.MatchContext;

/**
 * Handler for statement search
 */
public class StatementHandler extends MatchingHandler {

  @Override
  public boolean match(PsiElement patternNode, PsiElement matchedNode, MatchContext context) {
    if (!(matchedNode instanceof PsiStatement) && !(matchedNode instanceof PsiComment)) {
      // typed statement matches statements (including block statements) and comments
      return false;
    }

    final PsiExpression expression = ((PsiExpressionStatement)patternNode).getExpression();
    return context.getMatcher().match(expression, matchedNode);
  }
}
