// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.util;

import com.intellij.core.JavaPsiBundle;
import com.intellij.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Represents a kind of element that appears in Java source code.
 * The main purpose of this enum is to be able to display localized element name in UI
 */
public enum JavaElementKind {
  ABSTRACT_METHOD,
  ANNOTATION,
  CLASS,
  CONSTANT,
  CONSTRUCTOR,
  ENUM,
  ENUM_CONSTANT,
  EXPRESSION,
  FIELD,
  INITIALIZER,
  INTERFACE,
  LOCAL_VARIABLE,
  METHOD,
  MODULE,
  PACKAGE,
  PARAMETER,
  PATTERN_VARIABLE,
  RECORD,
  RECORD_COMPONENT,
  STATEMENT,
  UNKNOWN,
  VARIABLE;

  /**
   * @return human-readable name of the item having the subject role in the sentence (nominative case)
   */
  @Nls
  public @NotNull String subject() {
    return JavaPsiBundle.message("element." + name().toLowerCase(Locale.ROOT), 0);
  }

  /**
   * @return human-readable name of the item having the object role in the sentence (accusative case)
   */
  @Nls
  public @NotNull String object() {
    return JavaPsiBundle.message("element." + name().toLowerCase(Locale.ROOT), 1);
  }

  public static JavaElementKind fromElement(@NotNull PsiElement element) {
    if (element instanceof PsiClass) {
      PsiClass psiClass = (PsiClass)element;
      if (psiClass.isEnum()) {
        return ENUM;
      }
      if (psiClass.isRecord()) {
        return RECORD;
      }
      if (psiClass.isAnnotationType()) {
        return ANNOTATION;
      }
      if (psiClass.isInterface()) {
        return INTERFACE;
      }
      return CLASS;
    }
    if (element instanceof PsiMethod) {
      if (((PsiMethod)element).isConstructor()) {
        return CONSTRUCTOR;
      }
      if (((PsiMethod)element).hasModifierProperty(PsiModifier.ABSTRACT)) {
        return ABSTRACT_METHOD;
      }
      return METHOD;
    }
    if (element instanceof PsiField) {
      PsiField field = (PsiField)element;
      if (field instanceof PsiEnumConstant) {
        return ENUM_CONSTANT;
      }
      if (field.hasModifierProperty(PsiModifier.STATIC) && field.hasModifierProperty(PsiModifier.FINAL)) {
        return CONSTANT;
      }
      return FIELD;
    }
    if (element instanceof PsiRecordComponent) {
      return RECORD_COMPONENT;
    }
    if (element instanceof PsiLocalVariable) {
      return LOCAL_VARIABLE;
    }
    if (element instanceof PsiPatternVariable) {
      return PATTERN_VARIABLE;
    }
    if (element instanceof PsiParameter) {
      return PARAMETER;
    }
    if (element instanceof PsiVariable) {
      return VARIABLE;
    }
    if (element instanceof PsiPackage) {
      return PACKAGE;
    }
    if (element instanceof PsiJavaModule) {
      return MODULE;
    }
    if (element instanceof PsiClassInitializer) {
      return INITIALIZER;
    }
    if (element instanceof PsiStatement) {
      return STATEMENT;
    }
    if (element instanceof PsiExpression) {
      return EXPRESSION;
    }
    return UNKNOWN;
  }
}
