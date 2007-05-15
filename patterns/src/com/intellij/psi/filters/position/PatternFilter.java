/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.psi.filters.position;

import com.intellij.patterns.impl.MatchingContext;
import com.intellij.patterns.impl.Pattern;
import com.intellij.patterns.impl.TraverseContext;
import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ElementFilter;

/**
 * @author peter
 */
public class PatternFilter implements ElementFilter {
  private Pattern<? extends PsiElement,?> myPattern;

  public PatternFilter(final Pattern<? extends PsiElement, ?> pattern) {
    myPattern = pattern;
  }

  public boolean isAcceptable(Object element, PsiElement context) {
    return myPattern.accepts(element, new MatchingContext(), new TraverseContext());
  }

  public boolean isClassAcceptable(Class hintClass) {
    return true;
    //throw new UnsupportedOperationException("Method isClassAcceptable is not yet implemented in " + getClass().getName());
  }

  public String toString() {
    return myPattern.toString();
  }
}
