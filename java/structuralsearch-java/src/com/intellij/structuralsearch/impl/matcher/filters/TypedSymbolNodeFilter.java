// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher.filters;

import com.intellij.dupLocator.util.NodeFilter;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;

/**
 * Filter for typed symbols
 */
public class TypedSymbolNodeFilter implements NodeFilter {

  private static final NodeFilter INSTANCE = new TypedSymbolNodeFilter();

  private TypedSymbolNodeFilter() {}

  @Override
  public boolean accepts(PsiElement element) {
    if (element instanceof PsiClass) {
      return !(element instanceof PsiTypeParameter);
    }
    else if (element instanceof PsiMethod) {
      return true;
    }
    else if (element instanceof PsiJavaCodeReferenceElement) {
      if (PsiTreeUtil.getParentOfType(element, PsiImportStatement.class, PsiPackageStatement.class) != null) {
        return false;
      }
      final PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)element;
      final PsiReferenceParameterList parameterList = referenceElement.getParameterList();
      if (parameterList != null) {
        return true;
      }
    }
    return false;
  }

  public static NodeFilter getInstance() {
    return INSTANCE;
  }
}
