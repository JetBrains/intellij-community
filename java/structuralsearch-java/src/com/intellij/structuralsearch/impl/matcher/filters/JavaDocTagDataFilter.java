// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.impl.matcher.filters;

import com.intellij.dupLocator.util.NodeFilter;
import com.intellij.psi.PsiElement;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.javadoc.PsiDocToken;

public final class JavaDocTagDataFilter implements NodeFilter {

  private static final NodeFilter INSTANCE = new JavaDocTagDataFilter();

  private JavaDocTagDataFilter() {}

  @Override
  public boolean accepts(PsiElement element) {
    return element instanceof PsiDocTagValue || element instanceof PsiDocToken;
  }

  public static NodeFilter getInstance() {
    return INSTANCE;
  }
}
