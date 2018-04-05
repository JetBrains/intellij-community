// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.lang.jvm.*;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

class JavaAnnotationAttribute implements JvmAnnotationAttribute {

  private final PsiNameValuePair myPair;

  public JavaAnnotationAttribute(PsiNameValuePair pair) {
    myPair = pair;
  }

  @NotNull
  @Override
  public String getName() {
    String name = myPair.getName();
    return name != null ? name : "value";
  }

  @NotNull
  @Override
  public JvmAnnotationAttributeValue getValue() {
    return convertToJvm(myPair.getValue());
  }

  @NotNull
  private JvmAnnotationAttributeValue convertToJvm(PsiAnnotationMemberValue value) {
    if (value instanceof PsiLiteralExpression) {
      if (Objects
        .equals(((PsiLiteralExpression)value).getType(), PsiType.getJavaLangString(myPair.getManager(), myPair.getResolveScope()))) {
        return new MyJvmStringValue(((PsiLiteralExpression)value));
      }
      else {
        return new MyJvmPrimitiveValue(((PsiLiteralExpression)value));
      }
    }
    if (value instanceof PsiAnnotation) {
      return new MyJvmAnnotationValue(((PsiAnnotation)value));
    }
    if (value instanceof PsiReferenceExpression) {
      return new MyJvmConstantValue(((PsiReferenceExpression)value));
    }
    if (value instanceof PsiClassObjectAccessExpression) {
      return new MyJvmClassValue(((PsiClassObjectAccessExpression)value));
    }
    if (value instanceof PsiArrayInitializerMemberValue) {
      return new MyJvmAnnotationArrayValue(((PsiArrayInitializerMemberValue)value));
    }
    throw new RuntimeExceptionWithAttachments("Not implemented: " + (value != null ? value.getClass() : null),
                                              new Attachment("text", value != null ? value.getText() : "null"));
  }

  @Override
  public String toString() {
    return "JavaAnnotationAttribute[" + myPair + "]";
  }

  private static class MyValue<T extends PsiAnnotationMemberValue> implements JvmElement {

    protected final T myValue;

    private MyValue(T value) {myValue = value;}

    @Nullable
    @Override
    public PsiElement getSourceElement() {
      return myValue;
    }

    @Override
    public boolean isValid() {
      return myValue.isValid();
    }

    @Override
    public void navigate(boolean requestFocus) {

    }

    @Override
    public boolean canNavigate() {
      return false;
    }

    @Override
    public boolean canNavigateToSource() {
      return false;
    }
  }


  private class MyJvmStringValue extends MyValue<PsiLiteralExpression> implements JvmStringValue {

    public MyJvmStringValue(PsiLiteralExpression value) {
      super(value);
    }

    @NotNull
    @Override
    public String getValue() {
      return ((String)myValue.getValue());
    }
  }

  private class MyJvmPrimitiveValue extends MyValue<PsiLiteralExpression> implements JvmPrimitiveValue {

    public MyJvmPrimitiveValue(PsiLiteralExpression value) {
      super(value);
    }

    @NotNull
    @Override
    public Object getValue() {
      return myValue.getValue();
    }
  }

  private class MyJvmAnnotationValue extends MyValue<PsiAnnotation> implements JvmNestedAnnotationValue {

    public MyJvmAnnotationValue(PsiAnnotation value) {
      super(value);
    }

    @NotNull
    @Override
    public PsiAnnotation getValue() {
      return myValue;
    }
  }

  private class MyJvmConstantValue extends MyValue<PsiReferenceExpression> implements JvmConstantValue {

    public MyJvmConstantValue(PsiReferenceExpression value) {
      super(value);
    }

    @Nullable
    @Override
    public JvmField getField() {
      return (JvmField)myValue.resolve();
    }
  }

  private class MyJvmClassValue extends MyValue<PsiClassObjectAccessExpression> implements JvmClassValue {

    public MyJvmClassValue(PsiClassObjectAccessExpression value) {
      super(value);
    }

    @NotNull
    @Override
    public JvmClass getValue() {
      return (JvmClass)myValue.getOperand().getInnermostComponentReferenceElement().getReference().resolve();
    }
  }

  private class MyJvmAnnotationArrayValue extends MyValue<PsiArrayInitializerMemberValue> implements JvmAnnotationArrayValue {

    public MyJvmAnnotationArrayValue(PsiArrayInitializerMemberValue value) {
      super(value);
    }

    @NotNull
    @Override
    public List<JvmAnnotationAttributeValue> getValues() {
      return Arrays.stream(myValue.getInitializers()).map(e -> convertToJvm(e)).collect(Collectors.toList());
    }
  }
}
