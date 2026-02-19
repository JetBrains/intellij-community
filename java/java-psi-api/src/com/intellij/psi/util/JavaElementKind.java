// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util;

import com.intellij.core.JavaPsiBundle;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiCatchSection;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassInitializer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiForeachStatement;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.PsiLabeledStatement;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiPackageStatement;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiPatternVariable;
import com.intellij.psi.PsiReceiverParameter;
import com.intellij.psi.PsiRecordComponent;
import com.intellij.psi.PsiRecordHeader;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.PsiReferenceParameterList;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiTypeParameterList;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.javadoc.PsiSnippetDocTagBody;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

/**
 * Represents a kind of element that appears in Java source code.
 * The main purpose of this enum is to be able to display localized element name in UI
 */
public enum JavaElementKind {
  ABSTRACT_METHOD("element.abstract_method"),
  ANNOTATION("element.annotation"),
  ANONYMOUS_CLASS("element.anonymous_class"),
  CATCH_PARAMETER("element.catch_parameter"),
  CLASS("element.class"),
  CONSTANT("element.constant"),
  CONSTRUCTOR("element.constructor"),
  ENUM("element.enum"),
  ENUM_CONSTANT("element.enum_constant"),
  EXPRESSION("element.expression"),
  EXTENDS_LIST("element.extends.list"),
  FIELD("element.field"),
  FOR_PARAMETER("element.for_parameter"),
  INITIALIZER("element.initializer"),
  INTERFACE("element.interface"),
  LABEL("element.label"),
  LAMBDA_PARAMETER("element.lambda_parameter"),
  LOCAL_VARIABLE("element.local_variable"),
  METHOD("element.method"),
  METHOD_CALL("element.method.call"),
  MODULE("element.module"),
  PACKAGE("element.package"),
  PACKAGE_STATEMENT("element.package.statement"),
  PARAMETER("element.parameter"),
  PATTERN_VARIABLE("element.pattern_variable"),
  PERMITS_LIST("element.permits.list"),
  RECEIVER_PARAMETER("element.receiver.parameter"),
  RECORD("element.record"),
  RECORD_COMPONENT("element.record_component"),
  RECORD_HEADER("element.record_header"),
  SEMICOLON("element.type.semicolon"),
  SNIPPET_BODY("element.snippet_body"),
  STATEMENT("element.statement"),
  THROWS_LIST("element.throws.list"),
  TYPE_ARGUMENTS("element.type.arguments"),
  TYPE_PARAMETER("element.type.parameter"),
  TYPE_PARAMETERS("element.type.parameters"),
  UNKNOWN("element.unknown"),
  VARIABLE("element.variable");

  private final @PropertyKey(resourceBundle = JavaPsiBundle.BUNDLE) String propertyKey;

  JavaElementKind(@PropertyKey(resourceBundle = JavaPsiBundle.BUNDLE) String key) {
    propertyKey = key;
  }

  /**
   * @return human-readable name of the item having the subject role in the sentence (nominative case)
   */
  public @Nls @NotNull String subject() {
    return JavaPsiBundle.message(propertyKey, 0);
  }

  /**
   * @return human-readable name of the item having the object role in the sentence (accusative case)
   */
  public @Nls @NotNull String object() {
    return JavaPsiBundle.message(propertyKey, 1);
  }

  /**
   * @return less descriptive type for this type; usually result can be described in a single word 
   * (e.g. LOCAL_VARIABLE is replaced with VARIABLE).
   */
  public @NotNull JavaElementKind lessDescriptive() {
    switch (this) {
      case ABSTRACT_METHOD:
        return METHOD;
      case LOCAL_VARIABLE:
      case PATTERN_VARIABLE:
        return VARIABLE;
      case CONSTANT:
        return FIELD;
      case TYPE_PARAMETER:
      case ANONYMOUS_CLASS:
        return CLASS;
      case LAMBDA_PARAMETER:
      case CATCH_PARAMETER:
      case FOR_PARAMETER:
        return PARAMETER;
      default:
        return this;
    }
  }

  /**
   * @param element element to get the kind from
   * @return resulting kind
   */
  public static JavaElementKind fromElement(@NotNull PsiElement element) {
    if (element instanceof PsiClass) {
      PsiClass psiClass = (PsiClass)element;
      if (psiClass instanceof PsiAnonymousClass) {
        return ANONYMOUS_CLASS;
      }
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
      if (psiClass instanceof PsiTypeParameter) {
        return TYPE_PARAMETER;
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
    if (element instanceof PsiTypeParameterList) {
      return TYPE_PARAMETERS;
    }
    if (element instanceof PsiReferenceParameterList) {
      return TYPE_ARGUMENTS;
    }
    if (element instanceof PsiReferenceList) {
      PsiReferenceList.Role role = ((PsiReferenceList)element).getRole();
      if (role == PsiReferenceList.Role.THROWS_LIST) {
        return THROWS_LIST;
      }
      else if (role == PsiReferenceList.Role.EXTENDS_LIST) {
        return EXTENDS_LIST;
      }
      else if (role == PsiReferenceList.Role.PERMITS_LIST) {
        return PERMITS_LIST;
      }
    }
    if (element instanceof PsiAnnotation) {
      return ANNOTATION;
    }
    if (element instanceof PsiRecordComponent) {
      return RECORD_COMPONENT;
    }
    if (element instanceof PsiRecordHeader) {
      return RECORD_HEADER;
    }
    if (element instanceof PsiLocalVariable) {
      return LOCAL_VARIABLE;
    }
    if (element instanceof PsiPatternVariable) {
      return PATTERN_VARIABLE;
    }
    if (element instanceof PsiParameter) {
      PsiElement scope = ((PsiParameter)element).getDeclarationScope();
      if (scope instanceof PsiForeachStatement) {
        return FOR_PARAMETER;
      }
      if (scope instanceof PsiLambdaExpression) {
        return LAMBDA_PARAMETER;
      }
      if (scope instanceof PsiCatchSection) {
        return CATCH_PARAMETER;
      }
      return PARAMETER;
    }
    if (element instanceof PsiReceiverParameter) {
      return RECEIVER_PARAMETER;
    }
    if (element instanceof PsiVariable) {
      return VARIABLE;
    }
    if (element instanceof PsiPackage) {
      return PACKAGE;
    }
    if (element instanceof PsiPackageStatement) {
      return PACKAGE_STATEMENT;
    }
    if (element instanceof PsiJavaModule) {
      return MODULE;
    }
    if (element instanceof PsiClassInitializer) {
      return INITIALIZER;
    }
    if (element instanceof PsiLabeledStatement) {
      return LABEL;
    }
    if (element instanceof PsiStatement) {
      return STATEMENT;
    }
    if (element instanceof PsiMethodCallExpression) {
      return METHOD_CALL;
    }
    if (element instanceof PsiExpression) {
      return EXPRESSION;
    }
    if (element instanceof PsiSnippetDocTagBody) {
      return SNIPPET_BODY;
    }
    if (PsiUtil.isJavaToken(element, JavaTokenType.SEMICOLON)) {
      return SEMICOLON;
    }
    return UNKNOWN;
  }
}
