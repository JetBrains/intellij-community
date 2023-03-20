// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethodObject.reflect;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.ClassUtil;
import com.intellij.refactoring.extractMethodObject.ItemToReplaceDescriptor;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * @author Vitaliy.Bibaev
 */
public class MethodDescriptor implements ItemToReplaceDescriptor {
  private static final Logger LOG = Logger.getInstance(MethodDescriptor.class);

  private final PsiMethodCallExpression myCallExpression;
  private final PsiMethod myMethod;
  private final String myAccessibleReturnType;

  public MethodDescriptor(@NotNull PsiMethodCallExpression expression, @NotNull PsiMethod method) {
    myCallExpression = expression;
    myMethod = method;
    String returnType = PsiReflectionAccessUtil.getAccessibleReturnType(myCallExpression, resolveMethodReturnType(expression, method));
    if (returnType == null) {
      LOG.warn("Could not resolve method return type. java.lang.Object will be used instead");
      returnType = "java.lang.Object";
    }
    myAccessibleReturnType = returnType;
  }

  public static MethodDescriptor createIfInaccessible(@NotNull PsiClass outerClass, @NotNull PsiMethodCallExpression expression) {
    PsiMethod method = expression.resolveMethod();
    if (method != null && !Objects.equals(method.getContainingClass(), outerClass)) {
      return needReplace(outerClass, method, expression) ? new MethodDescriptor(expression, method) : null;
    }

    return null;
  }

  @Override
  public void replace(@NotNull PsiClass outerClass,
                      @NotNull PsiElementFactory elementFactory,
                      @NotNull PsiMethodCallExpression callExpression) {
    PsiClass containingClass = myMethod.getContainingClass();
    String containingClassName = containingClass == null ? null : ClassUtil.getJVMClassName(containingClass);
    String name = myMethod.getName();

    if (containingClassName == null) {
      LOG.warn("containing class for method \"" + name + "\" not found");
      return;
    }

    String newMethodName = PsiReflectionAccessUtil.getUniqueMethodName(outerClass, "call" + StringUtil.capitalize(name));
    ReflectionAccessMethodBuilder methodBuilder = new ReflectionAccessMethodBuilder(newMethodName);
    PsiMethod newMethod = methodBuilder.accessedMethod(containingClassName, myMethod.getName())
      .setStatic(outerClass.hasModifierProperty(PsiModifier.STATIC))
      .addParameter("java.lang.Object", "object")
      .addParameters(myMethod.getParameterList())
      .setReturnType(myAccessibleReturnType)
      .build(elementFactory, outerClass);

    outerClass.add(newMethod);
    String objectToCallOn = MemberQualifierUtil
      .findObjectExpression(myCallExpression.getMethodExpression(), myMethod, outerClass, callExpression, elementFactory);
    String args = StreamEx.of(myCallExpression.getArgumentList().getExpressions())
      .map(x -> x.getText())
      .prepend(objectToCallOn == null ? "null" : objectToCallOn)
      .joining(", ", "(", ")");
    String newMethodCallExpression = newMethod.getName() + args;

    myCallExpression.replace(elementFactory.createExpressionFromText(newMethodCallExpression, myCallExpression));
  }

  private static boolean needReplace(@NotNull PsiClass outerClass,
                                     @NotNull PsiMethod method,
                                     @NotNull PsiMethodCallExpression referenceExpression) {
    PsiExpression qualifier = referenceExpression.getMethodExpression().getQualifierExpression();
    return !PsiReflectionAccessUtil.isAccessibleMember(method, outerClass, qualifier);
  }

  @Nullable
  private static PsiType resolveMethodReturnType(@NotNull PsiMethodCallExpression callExpression, @NotNull PsiMethod method) {
    PsiSubstitutor substitutor = callExpression.resolveMethodGenerics().getSubstitutor();
    return substitutor.substitute(method.getReturnType());
  }
}
