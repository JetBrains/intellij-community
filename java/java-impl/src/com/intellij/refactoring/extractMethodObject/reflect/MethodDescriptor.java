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

  public MethodDescriptor(@NotNull PsiMethodCallExpression expression, @NotNull PsiMethod method) {
    myCallExpression = expression;
    myMethod = method;
  }

  public static MethodDescriptor createIfInaccessible(@NotNull PsiClass outerClass, @NotNull PsiMethodCallExpression expression) {
    PsiMethod method = expression.resolveMethod();
    if (method != null && !Objects.equals(method.getContainingClass(), outerClass)) {
      return needReplace(method, expression) ? new MethodDescriptor(expression, method) : null;
    }

    return null;
  }

  @Override
  public void replace(@NotNull PsiClass outerClass,
                      @NotNull PsiElementFactory elementFactory,
                      @NotNull PsiMethodCallExpression callExpression) {
    String returnType = PsiReflectionAccessUtil.getAccessibleReturnType(myCallExpression, resolveMethodReturnType());
    PsiClass containingClass = myMethod.getContainingClass();
    String containingClassName = containingClass == null ? null : ClassUtil.getJVMClassName(containingClass);
    String name = myMethod.getName();
    if (returnType == null) {
      LOG.warn("return type of" + myMethod.getName() + " method is null");
      return;
    }

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
      .setReturnType(returnType)
      .build(elementFactory, outerClass);

    outerClass.add(newMethod);
    String qualifier = qualify();
    String args = StreamEx.of(myCallExpression.getArgumentList().getExpressions())
      .map(x -> x.getText())
      .prepend(qualifier == null ? "null" : qualifier)
      .joining(", ", "(", ")");
    String newMethodCallExpression = newMethod.getName() + args;

    myCallExpression.replace(elementFactory.createExpressionFromText(newMethodCallExpression, myCallExpression));
  }

  private static boolean needReplace(@NotNull PsiMethod method, @NotNull PsiMethodCallExpression referenceExpression) {
    return !PsiReflectionAccessUtil.isAccessibleMember(method) ||
           !PsiReflectionAccessUtil.isQualifierAccessible(referenceExpression.getMethodExpression().getQualifierExpression());
  }

  @Nullable
  private PsiType resolveMethodReturnType() {
    PsiSubstitutor substitutor = myCallExpression.resolveMethodGenerics().getSubstitutor();
    return substitutor.substitute(myMethod.getReturnType());
  }

  @Nullable
  private String qualify() {
    String qualifier = PsiReflectionAccessUtil.extractQualifier(myCallExpression.getMethodExpression());
    if (qualifier == null) {
      if (!myMethod.hasModifierProperty(PsiModifier.STATIC)) {
        PsiClass containingClass = myMethod.getContainingClass();
        if (containingClass != null) {
          qualifier = containingClass.getQualifiedName() + ".this";
        }
      }
    }

    return qualifier;
  }
}
