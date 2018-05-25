// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher.filters;

import com.intellij.dupLocator.util.NodeFilter;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.PsiResourceExpression;

/**
 * Filters expression nodes
 */
public class ExpressionFilter implements NodeFilter {

  private static final NodeFilter INSTANCE = new ExpressionFilter();

  public static NodeFilter getInstance() {
    return INSTANCE;
  }

  private ExpressionFilter() {}

  @Override
  public boolean accepts(PsiElement element) {
    return element instanceof PsiExpression || element instanceof PsiNameValuePair || element instanceof PsiResourceExpression;
  }
}
