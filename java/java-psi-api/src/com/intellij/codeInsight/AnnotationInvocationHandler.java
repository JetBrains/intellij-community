/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInsight;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiNameValuePair;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.lang.annotation.IncompleteAnnotationException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Arrays;

class AnnotationInvocationHandler implements InvocationHandler {
  @NotNull private final Class<? extends Annotation> type;
  @NotNull private final PsiAnnotation myAnnotation;

  AnnotationInvocationHandler(@NotNull Class<? extends Annotation> type, @NotNull PsiAnnotation annotation) {
    this.type = type;
    myAnnotation = annotation;
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) {
    Class<?>[] paramTypes = method.getParameterTypes();
    assert paramTypes.length == 0: Arrays.toString(paramTypes);

    String member = method.getName();
    if (member.equals("toString")) {
      return toStringImpl();
    }
    if (member.equals("annotationType")) {
      return type;
    }

    // Handle annotation member accessors
    PsiAnnotationMemberValue value = myAnnotation.findAttributeValue(member);
    if (value == null) {
      throw new IncompleteAnnotationException(type, member+". (Unable to find attribute in '"+myAnnotation.getText()+"')");
    }

    Object result = JavaPsiFacade.getInstance(myAnnotation.getProject()).getConstantEvaluationHelper().computeConstantExpression(value);

    if (result == null) {
      throw new IncompleteAnnotationException(type, member+". (Unable to evaluate annotation value '"+value+"')");
    }

    // todo arrays
    return result;
  }

  /**
   * Implementation of dynamicProxy.toString()
   */
  private String toStringImpl() {
    StringBuilder result = new StringBuilder(128);
    result.append('@');
    result.append(type.getName());
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
