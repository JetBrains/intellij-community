// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher.filters;

import com.intellij.dupLocator.util.NodeFilter;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;

public final class ConstantFilter implements NodeFilter {

  private static final NodeFilter INSTANCE = new ConstantFilter();

  private ConstantFilter() {}

  @Override
  public boolean accepts(PsiElement element) {
    return element instanceof PsiLiteralExpression;
  }

  public static NodeFilter getInstance() {
    return INSTANCE;
  }
}
