// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher.filters;

import com.intellij.dupLocator.util.NodeFilter;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;

public final class ClassFilter implements NodeFilter {

  private static final NodeFilter INSTANCE = new ClassFilter();

  private ClassFilter() {}

  @Override
  public boolean accepts(PsiElement element) {
    return element instanceof PsiClass;
  }

  public static NodeFilter getInstance() {
    return INSTANCE;
  }
}
