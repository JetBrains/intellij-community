// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.removemiddleman;

import com.intellij.psi.*;
import com.intellij.util.ArrayUtil;

import java.util.HashSet;
import java.util.Set;

public final class DelegationUtils {
  private DelegationUtils() {
    super();
  }



  public static Set<PsiMethod> getDelegatingMethodsForField(PsiField field) {
    final Set<PsiMethod> out = new HashSet<>();
    final PsiClass containingClass = field.getContainingClass();
    if (containingClass == null) {
      return out;
    }
    final PsiMethod[] methods = containingClass.getMethods();
    for (PsiMethod method : methods) {
      if (isDelegation(field, method)) {
        out.add(method);
      }
    }
    return out;
  }

  private static boolean isDelegation(PsiField field, PsiMethod method) {
    if (method.isConstructor()) {
      return false;
    }
    final PsiCodeBlock body = method.getBody();
    if (body == null) {
      return false;
    }
    final PsiStatement[] statements = body.getStatements();
    if (statements.length != 1) {
      return false;
    }
    final PsiStatement statement = statements[0];
    if (statement instanceof PsiReturnStatement) {
      final PsiExpression returnValue = ((PsiReturnStatement)statement).getReturnValue();
      if (!isDelegationCall(returnValue, field, method)) {
        return false;
      }
    }
    else if (statement instanceof PsiExpressionStatement) {
      final PsiExpression value = ((PsiExpressionStatement)statement).getExpression();
      if (!isDelegationCall(value, field, method)) {
        return false;
      }
    }
    else {
      return false;
    }

    return true;
  }

  public static boolean isAbstract(PsiMethod method) {
    if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
      return true;
    }
    return method.getContainingClass().isInterface();
  }

  private static boolean isDelegationCall(PsiExpression expression, PsiField field, PsiMethod method) {
    if (!(expression instanceof PsiMethodCallExpression)) {
      return false;
    }
    final PsiMethodCallExpression call = (PsiMethodCallExpression)expression;
    final PsiReferenceExpression methodExpression = call.getMethodExpression();
    final PsiExpression qualifier = methodExpression.getQualifierExpression();
    if (!(qualifier instanceof PsiReferenceExpression)) {
      return false;
    }
    final PsiElement referent = ((PsiReference)qualifier).resolve();
    if (referent == null || !referent.equals(field)) {
      return false;
    }
    final PsiExpressionList argumentList = call.getArgumentList();
    final PsiExpression[] args = argumentList.getExpressions();
    for (PsiExpression arg : args) {
      if (!isParameterReference(arg, method)) {
        return false;
      }
    }
    return true;
  }

  private static boolean isParameterReference(PsiExpression arg, PsiMethod method) {
    if (!(arg instanceof PsiReferenceExpression)) {
      return false;
    }
    final PsiElement referent = ((PsiReference)arg).resolve();
    if (!(referent instanceof PsiParameter)) {
      return false;
    }
    final PsiElement declarationScope = ((PsiParameter)referent).getDeclarationScope();
    return method.equals(declarationScope);
  }


  public static int[] getParameterPermutation(PsiMethod method) {
    final PsiCodeBlock body = method.getBody();
    assert body != null;
    final PsiStatement[] statements = body.getStatements();
    final PsiStatement statement = statements[0];
    final PsiParameterList parameterList = method.getParameterList();
    if (statement instanceof PsiReturnStatement) {
      final PsiExpression returnValue = ((PsiReturnStatement)statement).getReturnValue();
      final PsiMethodCallExpression call = (PsiMethodCallExpression)returnValue;
      return calculatePermutation(call, parameterList);
    }
    else {
      final PsiExpression value = ((PsiExpressionStatement)statement).getExpression();
      final PsiMethodCallExpression call = (PsiMethodCallExpression)value;
      return calculatePermutation(call, parameterList);
    }
  }

  private static int[] calculatePermutation(PsiMethodCallExpression call, PsiParameterList parameterList) {
    final PsiExpressionList argumentList = call.getArgumentList();
    final PsiExpression[] args = argumentList.getExpressions();
    final int[] out = ArrayUtil.newIntArray(args.length);
    for (int i = 0; i < args.length; i++) {
      final PsiExpression arg = args[i];
      final PsiParameter parameter = (PsiParameter)((PsiReference)arg).resolve();
      out[i] = parameterList.getParameterIndex(parameter);
    }
    return out;
  }

  public static PsiMethod getDelegatedMethod(PsiMethod method) {
    final PsiCodeBlock body = method.getBody();
    assert body != null;
    final PsiStatement[] statements = body.getStatements();
    final PsiStatement statement = statements[0];
    if (statement instanceof PsiReturnStatement) {
      final PsiExpression returnValue = ((PsiReturnStatement)statement).getReturnValue();
      final PsiMethodCallExpression call = (PsiMethodCallExpression)returnValue;
      assert call != null;
      return call.resolveMethod();
    }
    else {
      final PsiExpression value = ((PsiExpressionStatement)statement).getExpression();
      final PsiMethodCallExpression call = (PsiMethodCallExpression)value;
      return call.resolveMethod();
    }
  }
}
