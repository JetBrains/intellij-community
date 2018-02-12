/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.structuralsearch.impl.matcher.filters;

import com.intellij.dupLocator.util.NodeFilter;
import com.intellij.psi.*;

/**
 * Filter for typed symbols
 */
public class TypedSymbolNodeFilter implements NodeFilter {

  private static final NodeFilter INSTANCE = new TypedSymbolNodeFilter();

  private TypedSymbolNodeFilter() {}

  @Override
  public boolean accepts(PsiElement element) {
    if (element instanceof PsiClass) {
      if (element instanceof PsiTypeParameter) {
        return false;
      }
      final PsiClass aClass = (PsiClass)element;
      return aClass.hasTypeParameters();
    }
    else if (element instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)element;
      return method.hasTypeParameters();
    }
    else if (element instanceof PsiJavaCodeReferenceElement) {
      final PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)element;
      final PsiReferenceParameterList parameterList = referenceElement.getParameterList();
      if (parameterList != null) {
        return parameterList.getTypeParameterElements().length > 0;
      }
    }
    return false;
  }

  public static NodeFilter getInstance() {
    return INSTANCE;
  }
}
