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
