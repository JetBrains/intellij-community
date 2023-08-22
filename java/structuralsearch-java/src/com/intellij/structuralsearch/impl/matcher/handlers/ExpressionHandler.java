// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher.handlers;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.structuralsearch.impl.matcher.MatchContext;
import org.jetbrains.annotations.NotNull;

/**
 * Handler for expression search. The pattern for an expression includes an unnecessary {@code PsiExpressionStatement},
 * this is skipped by this {@code MatchingHandler}
 */
public class ExpressionHandler extends MatchingHandler {
  @Override
  public boolean match(PsiElement patternNode, PsiElement matchedNode, @NotNull MatchContext context) {
    if (!super.match(patternNode,matchedNode, context)) {
      return false;
    }

    return context.getMatcher().match(((PsiExpressionStatement)patternNode).getExpression(), matchedNode);
  }
}
