/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.psi.filters.position;

import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ElementFilter;

/**
 * @author peter
 */
public class PatternFilter implements ElementFilter {
  private ElementPattern<?> myPattern;

  public PatternFilter(final ElementPattern<?> pattern) {
    myPattern = pattern;
  }

  public boolean isAcceptable(Object element, PsiElement context) {
    return myPattern.accepts(element);
  }

  public boolean isClassAcceptable(Class hintClass) {
    return true;
    //throw new UnsupportedOperationException("Method isClassAcceptable is not yet implemented in " + getClass().getName());
  }

  public String toString() {
    return myPattern.toString();
  }
}
