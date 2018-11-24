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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiTypeParameter;

public class TypeParameterFilter implements NodeFilter {

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
