// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher.filters;

import com.intellij.dupLocator.util.NodeFilter;
import com.intellij.psi.*;

/**
 * Tree filter for searching symbols ('T)
 */
public class SymbolNodeFilter implements NodeFilter {

  private static final NodeFilter INSTANCE = new SymbolNodeFilter();

  private SymbolNodeFilter() {}

  @Override
  public boolean accepts(PsiElement element) {
    return element instanceof PsiExpression || element instanceof PsiAnnotation || element instanceof PsiClass ||
           element instanceof PsiMethod || element instanceof PsiVariable || element instanceof PsiJavaCodeReferenceElement ||
           element instanceof PsiNameValuePair || element instanceof PsiLabeledStatement;
  }

  public static NodeFilter getInstance() {
    return INSTANCE;
  }
}
