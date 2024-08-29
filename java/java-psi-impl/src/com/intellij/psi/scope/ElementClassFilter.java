// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.scope;

import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;

public final class ElementClassFilter implements ElementFilter {
  public static final ElementClassFilter PACKAGE = new ElementClassFilter(ElementClassHint.DeclarationKind.PACKAGE);
  public static final ElementClassFilter VARIABLE = new ElementClassFilter(ElementClassHint.DeclarationKind.VARIABLE);
  public static final ElementClassFilter METHOD = new ElementClassFilter(ElementClassHint.DeclarationKind.METHOD);
  public static final ElementClassFilter CLASS = new ElementClassFilter(ElementClassHint.DeclarationKind.CLASS);
  public static final ElementClassFilter FIELD = new ElementClassFilter(ElementClassHint.DeclarationKind.FIELD);
  public static final ElementClassFilter ENUM_CONST = new ElementClassFilter(ElementClassHint.DeclarationKind.ENUM_CONST);

  private final ElementClassHint.DeclarationKind myKind;

  private ElementClassFilter(ElementClassHint.DeclarationKind kind) {
    myKind = kind;
  }

  @Override
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

  @Override
  public boolean isClassAcceptable(Class hintClass) {
    return true;
  }
}