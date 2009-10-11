/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.psi.scope;

import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;

public class ElementClassFilter implements ElementFilter {
  public static final ElementClassFilter PACKAGE_FILTER = new ElementClassFilter(ElementClassHint.DeclaractionKind.PACKAGE);
  public static final ElementClassFilter VARIABLE = new ElementClassFilter(ElementClassHint.DeclaractionKind.VARIABLE);
  public static final ElementClassFilter METHOD = new ElementClassFilter(ElementClassHint.DeclaractionKind.METHOD);
  public static final ElementClassFilter CLASS = new ElementClassFilter(ElementClassHint.DeclaractionKind.CLASS);
  public static final ElementClassFilter FIELD = new ElementClassFilter(ElementClassHint.DeclaractionKind.FIELD);
  public static final ElementClassFilter ENUM_CONST = new ElementClassFilter(ElementClassHint.DeclaractionKind.ENUM_CONST);

  private final ElementClassHint.DeclaractionKind myKind;
  
  private ElementClassFilter(ElementClassHint.DeclaractionKind kind) {
    myKind = kind;
  }

  public boolean isAcceptable(Object element, PsiElement context) {
    switch (myKind) {
      case CLASS:
        return element instanceof PsiClass;

      case ENUM_CONST:
        return element instanceof PsiEnumConstant;

      case FIELD:
        return element instanceof PsiField;

      case METHOD:
        return element instanceof PsiMethod;

      case PACKAGE:
        return element instanceof PsiPackage;

      case VARIABLE:
        return element instanceof PsiVariable;
    }

    return false;
  }

  public boolean isClassAcceptable(Class hintClass) {
    return true;
  }
}
