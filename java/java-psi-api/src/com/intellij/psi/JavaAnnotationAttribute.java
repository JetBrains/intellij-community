// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.lang.jvm.*;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

class JavaAnnotationAttribute implements JvmAnnotationAttribute {

  @Nullable
  @Override
  public JvmAnnotationTreeElement getParentInAnnotation() {
    return myAnnotation;
  }

  private final PsiNameValuePair myPair;
  private final PsiAnnotation myAnnotation;

  public JavaAnnotationAttribute(PsiNameValuePair pair, PsiAnnotation annotation) {
    myPair = pair;
    myAnnotation = annotation;
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
    PsiAnnotationMemberValue value = myPair.getValue();
    if (value instanceof PsiLiteralExpression) {
      if (Objects
        .equals(((PsiLiteralExpression)value).getType(), PsiType.getJavaLangString(myPair.getManager(), myPair.getResolveScope()))) {
        return new MyJvmStringValue(value);
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
    throw new RuntimeExceptionWithAttachments("Not implemented: " + (value != null ? value.getClass() : null),
                                              new Attachment("text", value != null ? value.getText() : "null"));
  }

  @Override
  public String toString() {
    return "JavaAnnotationAttribute[" + myPair + "]";
  }

  private class MyValue<T extends PsiAnnotationMemberValue> implements JvmElement {

    protected final T myValue;

    private MyValue(T value) {myValue = value;}

    @Nullable
    public JvmAnnotationTreeElement getParentInAnnotation() {
      return JavaAnnotationAttribute.this;
    }

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


  private class MyJvmStringValue extends MyValue<PsiAnnotationMemberValue> implements JvmStringValue {

    public MyJvmStringValue(PsiAnnotationMemberValue value) {
      super(value);
    }

    @NotNull
    @Override
    public String getValue() {
      return myValue.toString();
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
}
