// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher.filters;

import com.intellij.dupLocator.util.NodeFilter;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiLabeledStatement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.PsiVariable;

/**
 * Tree filter for searching symbols ('T)
 */
public final class SymbolNodeFilter implements NodeFilter {

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
