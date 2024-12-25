// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.internationalization;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInsight.AnnotationUtil.CHECK_EXTERNAL;

public final class NonNlsUtils {

  private static final Key<Boolean> KEY = new Key<>("IG_NON_NLS_ANNOTATED_USE");

  private NonNlsUtils() {
  }

  public static @Nullable PsiModifierListOwner getAnnotatableArgument(
    PsiMethodCallExpression methodCallExpression) {
    final PsiExpressionList argumentList =
      methodCallExpression.getArgumentList();
    final PsiExpression[] arguments = argumentList.getExpressions();
    if (arguments.length < 1) {
      return null;
    }
    final PsiExpression argument = arguments[0];
    if (argument instanceof PsiReferenceExpression referenceExpression) {
      final PsiElement element = referenceExpression.resolve();
      if (element instanceof PsiModifierListOwner) {
        return (PsiModifierListOwner)element;
      }
    }
    return null;
  }

  public static @Nullable PsiModifierListOwner getAnnotatableQualifier(
    PsiReferenceExpression expression) {
    final PsiExpression qualifierExpression =
      expression.getQualifierExpression();
    if (qualifierExpression instanceof PsiReferenceExpression referenceExpression) {
      final PsiElement element = referenceExpression.resolve();
      if (element instanceof PsiModifierListOwner && !(element instanceof PsiClass)) {
        return (PsiModifierListOwner)element;
      }
    }
    return null;
  }

  public static boolean isNonNlsAnnotated(
    @Nullable PsiExpression expression) {
    if (isReferenceToNonNlsAnnotatedElement(expression)) {
      return true;
    }
    if (expression instanceof PsiMethodCallExpression methodCallExpression) {
      final PsiMethod method = methodCallExpression.resolveMethod();
      if (isNonNlsAnnotatedModifierListOwner(method)) {
        return true;
      }
      final PsiReferenceExpression methodExpression =
        methodCallExpression.getMethodExpression();
      final PsiExpression qualifier =
        methodExpression.getQualifierExpression();
      return isNonNlsAnnotated(qualifier);
    }
    else if (expression instanceof PsiArrayAccessExpression arrayAccessExpression) {
      final PsiExpression arrayExpression =
        arrayAccessExpression.getArrayExpression();
      return isNonNlsAnnotated(arrayExpression);
    }
    return false;
  }

  public static boolean isNonNlsAnnotatedUse(
    @Nullable PsiExpression expression) {
    if (expression == null) {
      return false;
    }
    final Boolean value = getCachedValue(expression, KEY);
    if (value != null) {
      return value.booleanValue();
    }
    PsiElement element = expression;
    while(true) {
      element = PsiTreeUtil.getParentOfType(element,
                                            PsiFunctionalExpression.class,
                                            PsiExpressionList.class,
                                            PsiAssignmentExpression.class,
                                            PsiVariable.class,
                                            PsiReturnStatement.class);
      if (!(element instanceof PsiFunctionalExpression)) {
        break;
      }
      PsiElement parent = PsiUtil.skipParenthesizedExprUp(element.getParent());
      if (parent instanceof PsiExpressionList) {
        element = parent;
      }
    }
    final boolean result;
    if (element instanceof PsiExpressionList expressionList) {
      result = isNonNlsAnnotatedParameter(expression, expressionList);
    }
    else if (element instanceof PsiVariable) {
      result = isNonNlsAnnotatedModifierListOwner(element);
    }
    else if (element instanceof PsiAssignmentExpression assignmentExpression) {
      result =
        isAssignmentToNonNlsAnnotatedVariable(assignmentExpression);
    }
    else if (element instanceof PsiReturnStatement) {
      final PsiMethod method =
        PsiTreeUtil.getParentOfType(element, PsiMethod.class);
      result = isNonNlsAnnotatedModifierListOwner(method);
    }
    else {
      result = false;
    }
    putCachedValue(expression, KEY, Boolean.valueOf(result));
    return result;
  }

  private static <T> void putCachedValue(PsiExpression expression,
                                         Key<T> key, T value) {
    if (expression instanceof PsiBinaryExpression) {
      expression.putUserData(key, value);
    }
  }

  private static @Nullable <T> T getCachedValue(PsiExpression expression, Key<T> key) {
    final T data = expression.getUserData(key);
    if (!(expression instanceof PsiBinaryExpression binaryExpression)) {
      return data;
    }
    final PsiExpression lhs = binaryExpression.getLOperand();
    T childData = null;
    if (lhs instanceof PsiBinaryExpression) {
      childData = lhs.getUserData(key);
    }
    if (childData == null) {
      final PsiExpression rhs = binaryExpression.getROperand();
      if (rhs instanceof PsiBinaryExpression) {
        childData = rhs.getUserData(key);
      }
    }
    if (childData != data) {
      expression.putUserData(key, childData);
    }
    return childData;
  }

  private static boolean isAssignmentToNonNlsAnnotatedVariable(
    PsiAssignmentExpression assignmentExpression) {
    final PsiExpression lhs = assignmentExpression.getLExpression();
    return isReferenceToNonNlsAnnotatedElement(lhs);
  }

  private static boolean isReferenceToNonNlsAnnotatedElement(
    @Nullable PsiExpression expression) {
    if (!(expression instanceof PsiReferenceExpression referenceExpression)) {
      return false;
    }
    final PsiElement target = referenceExpression.resolve();
    return isNonNlsAnnotatedModifierListOwner(target);
  }

  private static boolean isNonNlsAnnotatedParameter(
    PsiExpression expression,
    PsiExpressionList expressionList) {
    final PsiElement parent = expressionList.getParent();
    final PsiParameterList parameterList;
    if (parent instanceof PsiMethodCallExpression methodCallExpression) {
      if (isQualifierNonNlsAnnotated(methodCallExpression)) {
        return true;
      }
      final PsiMethod method = methodCallExpression.resolveMethod();
      if (method == null) {
        return false;
      }
      parameterList = method.getParameterList();
    }
    else if (parent instanceof PsiNewExpression newExpression) {
      final PsiMethod constructor = newExpression.resolveConstructor();
      if (constructor == null) {
        return false;
      }
      parameterList = constructor.getParameterList();
    }
    else {
      return false;
    }
    final PsiExpression[] expressions = expressionList.getExpressions();
    int index = -1;
    for (int i = 0; i < expressions.length; i++) {
      final PsiExpression argument = expressions[i];
      if (PsiTreeUtil.isAncestor(argument, expression, false)) {
        index = i;
      }
    }
    final PsiParameter[] parameters = parameterList.getParameters();
    if (parameters.length == 0) {
      return false;
    }
    final PsiParameter parameter;
    if (index < parameters.length) {
      parameter = parameters[index];
    }
    else {
      parameter = parameters[parameters.length - 1];
    }
    return isNonNlsAnnotatedModifierListOwner(parameter);
  }

  private static boolean isQualifierNonNlsAnnotated(
    PsiMethodCallExpression methodCallExpression) {
    final PsiReferenceExpression methodExpression =
      methodCallExpression.getMethodExpression();
    final PsiExpression qualifier =
      methodExpression.getQualifierExpression();
    if (isReferenceToNonNlsAnnotatedElement(qualifier)) {
      return true;
    }
    if (qualifier instanceof PsiMethodCallExpression) {
      final PsiMethod method = methodCallExpression.resolveMethod();
      if (method == null) {
        return false;
      }
      if (MethodUtils.isChainable(method)) {
        final PsiMethodCallExpression expression =
          (PsiMethodCallExpression)qualifier;
        if (isQualifierNonNlsAnnotated(expression)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean isNonNlsAnnotatedModifierListOwner(
    @Nullable PsiElement element) {
    if (!(element instanceof PsiModifierListOwner variable)) {
      return false;
    }
    return AnnotationUtil.isAnnotated(variable, AnnotationUtil.NON_NLS, CHECK_EXTERNAL);
  }
}