// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.lang.jvm.JvmAnnotation;
import com.intellij.lang.jvm.JvmClass;
import com.intellij.lang.jvm.JvmEnumField;
import com.intellij.lang.jvm.annotation.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

import static com.intellij.util.containers.ContainerUtil.map;

abstract class PsiAnnotationAttributeValue<T extends PsiAnnotationMemberValue> implements JvmAnnotationAttributeValue {

  protected final T myElement;

  protected PsiAnnotationAttributeValue(@NotNull T value) {
    myElement = value;
  }
}

class PsiAnnotationConstantValue extends PsiAnnotationAttributeValue<PsiExpression> implements JvmAnnotationConstantValue {

  PsiAnnotationConstantValue(@NotNull PsiExpression value) {
    super(value);
  }

  @Override
  public @Nullable Object getConstantValue() {
    PsiConstantEvaluationHelper evaluationHelper = JavaPsiFacade.getInstance(myElement.getProject()).getConstantEvaluationHelper();
    return evaluationHelper.computeConstantExpression(myElement);
  }
}

class PsiAnnotationClassValue extends PsiAnnotationAttributeValue<PsiClassObjectAccessExpression> implements JvmAnnotationClassValue {

  PsiAnnotationClassValue(@NotNull PsiClassObjectAccessExpression value) {
    super(value);
  }

  private PsiJavaCodeReferenceElement getReferenceElement() {
    return myElement.getOperand().getInnermostComponentReferenceElement();
  }

  @Override
  public @Nullable String getQualifiedName() {
    final PsiJavaCodeReferenceElement referenceElement = getReferenceElement();
    return referenceElement == null ? null : referenceElement.getQualifiedName();
  }

  @Override
  public @Nullable JvmClass getClazz() {
    PsiJavaCodeReferenceElement referenceElement = getReferenceElement();
    if (referenceElement == null) return null;
    PsiElement resolved = referenceElement.resolve();
    return resolved instanceof JvmClass ? (JvmClass)resolved : null;
  }
}

class PsiNestedAnnotationValue extends PsiAnnotationAttributeValue<PsiAnnotation> implements JvmNestedAnnotationValue {

  PsiNestedAnnotationValue(@NotNull PsiAnnotation value) {
    super(value);
  }

  @Override
  public @NotNull JvmAnnotation getValue() {
    return myElement;
  }
}

class PsiAnnotationEnumFieldValue extends PsiAnnotationAttributeValue<PsiReferenceExpression> implements JvmAnnotationEnumFieldValue {

  private final JvmEnumField myEnumField;

  PsiAnnotationEnumFieldValue(@NotNull PsiReferenceExpression value, @NotNull JvmEnumField field) {
    super(value);
    myEnumField = field;
  }

  @Override
  public @Nullable JvmEnumField getField() {
    return myEnumField;
  }
}

class PsiAnnotationArrayValue extends PsiAnnotationAttributeValue<PsiArrayInitializerMemberValue> implements JvmAnnotationArrayValue {

  PsiAnnotationArrayValue(@NotNull PsiArrayInitializerMemberValue value) {
    super(value);
  }

  @Override
  public @NotNull @Unmodifiable List<JvmAnnotationAttributeValue> getValues() {
    return map(myElement.getInitializers(), PsiJvmConversionHelper::getAnnotationAttributeValue);
  }
}
