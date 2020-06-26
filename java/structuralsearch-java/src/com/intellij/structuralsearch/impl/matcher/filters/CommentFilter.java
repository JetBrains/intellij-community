// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher.filters;

import com.intellij.dupLocator.util.NodeFilter;
import com.intellij.psi.*;

public final class CommentFilter implements NodeFilter {

  private static final NodeFilter INSTANCE = new CommentFilter();

  private CommentFilter() {}

  @Override
  public boolean accepts(PsiElement element) {
    return element instanceof PsiComment || element instanceof PsiClass || element instanceof PsiMethod || element instanceof PsiField;
  }

  public static NodeFilter getInstance() {
    return INSTANCE;
  }
}
