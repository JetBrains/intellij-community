// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher.handlers;

import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.impl.matcher.MatchContext;
import com.intellij.structuralsearch.impl.matcher.filters.AnnotationFilter;

/**
 * @author Bas
 */
public class AnnotationHandler extends MatchingHandler {

  public AnnotationHandler() {
    setFilter(AnnotationFilter.getInstance());
  }

  @Override
  public boolean match(PsiElement patternNode, PsiElement matchedNode, MatchContext context) {
    if (!super.match(patternNode,matchedNode,context)) {
      return false;
    }

    final PsiElement element = patternNode.getFirstChild().getFirstChild();
    return context.getMatcher().match(element, matchedNode);
  }
}
