// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiNameValuePair;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.annotation.IncompleteAnnotationException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Arrays;

class AnnotationInvocationHandler implements InvocationHandler {
  private final @NotNull Class<? extends Annotation> myType;
  private final @NotNull PsiAnnotation myAnnotation;

  AnnotationInvocationHandler(@NotNull Class<? extends Annotation> type, @NotNull PsiAnnotation annotation) {
    myType = type;
    myAnnotation = annotation;
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) {
    assert method.getParameterCount() == 0: Arrays.toString(method.getParameterTypes());

    String member = method.getName();
    if (member.equals("toString")) {
      return toStringImpl();
    }
    if (member.equals("annotationType")) {
      return myType;
    }

    // Handle annotation member accessors
    Pair<Object, String> pair = attributeValueOrError(myAnnotation, myType, member);
    Object value = pair.first;

    if (value == null) {
      String error = pair.second;
      String message = member + ". (Unable to find attribute in '" + myAnnotation.getText() + "': " + error + ")";
      throw new IncompleteAnnotationException(myType, message);
    }

    return value;
  }

  private static @NotNull Pair<Object, String> attributeValueOrError(@NotNull PsiAnnotation annotation,
                                                                     Class<? extends Annotation> type,
                                                                     @Nullable @NonNls String attributeName) {
    PsiNameValuePair attribute = AnnotationUtil.findDeclaredAttribute(annotation, attributeName);
    final PsiAnnotationMemberValue value = attribute == null ? null : attribute.getValue();

    if (value != null) {
      Object result = JavaPsiFacade.getInstance(annotation.getProject()).getConstantEvaluationHelper().computeConstantExpression(value);

      if (result == null) {
        return Pair.create(null, "Unable to evaluate annotation value '" + value.getText() + "'");
      }

      // todo arrays
      return Pair.create(result, null);
    }

    if (attributeName == null) attributeName = "value";
    Method method;
    try {
      method = type.getMethod(attributeName);
    }
    catch (NoSuchMethodException e) {
      return Pair.create(null, "Method not found: " + attributeName);
    }
    Object defaultValue = method.getDefaultValue();
    if (defaultValue == null) {
      return Pair.create(null, "No default value is specified for method " + attributeName);
    }
    return Pair.create(defaultValue, null);
  }

  /**
   * Implementation of dynamicProxy.toString()
   */
  private String toStringImpl() {
    StringBuilder result = new StringBuilder(128);
    result.append('@');
    result.append(myType.getName());
    result.append('(');
    boolean firstMember = true;
    PsiNameValuePair[] attributes = myAnnotation.getParameterList().getAttributes();
    for (PsiNameValuePair e : attributes) {
      if (firstMember) {
        firstMember = false;
      }
      else {
        result.append(", ");
      }

      result.append(e.getName());
      result.append('=');
      PsiAnnotationMemberValue value = e.getValue();
      result.append(value == null ? "null" : value.getText());
    }
    result.append(')');
    return result.toString();
  }
}
