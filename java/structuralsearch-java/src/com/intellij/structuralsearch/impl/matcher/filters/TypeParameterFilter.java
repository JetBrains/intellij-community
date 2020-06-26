// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher.filters;

import com.intellij.dupLocator.util.NodeFilter;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiTypeParameter;

public final class TypeParameterFilter implements NodeFilter {

  private static final NodeFilter INSTANCE = new TypeParameterFilter();

  private TypeParameterFilter() {}

  @Override
  public boolean accepts(PsiElement element) {
    return element instanceof PsiTypeElement || element instanceof PsiTypeParameter || element instanceof PsiJavaCodeReferenceElement;
  }

  public static NodeFilter getInstance() {
    return INSTANCE;
  }
}
