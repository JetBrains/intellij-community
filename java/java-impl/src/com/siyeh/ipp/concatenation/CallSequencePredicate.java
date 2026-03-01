// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.concatenation;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
class CallSequencePredicate implements PsiElementPredicate {

  @Override
  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiExpressionStatement statement)) {
      return false;
    }
    final PsiStatement nextSibling = PsiTreeUtil.getNextSiblingOfType(statement, PsiStatement.class);
    if (nextSibling == null) {
      return false;
    }
    final PsiVariable variable1 = getVariable(statement);
    if (variable1 == null) {
      return false;
    }
    final PsiVariable variable2 = getVariable(nextSibling);
    return variable1.equals(variable2);
  }

  private static @Nullable PsiVariable getVariable(PsiStatement statement) {
    if (!(statement instanceof PsiExpressionStatement expressionStatement)) {
      return null;
    }
    final PsiExpression expression = expressionStatement.getExpression();
    if (!(expression instanceof PsiMethodCallExpression methodCallExpression)) {
      return null;
    }
    return getVariable(methodCallExpression);
}
 private static @Nullable PsiVariable getVariable(PsiMethodCallExpression methodCallExpression) {
   final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(methodCallExpression.getType());
   if (aClass == null) {
     return null;
   }
   final PsiMethod method = methodCallExpression.resolveMethod();
   if (method == null) {
     return null;
   }
   final PsiClass containingClass = method.getContainingClass();
   if (!aClass.equals(containingClass)) {
     return null;
   }
   final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
   final PsiExpression qualifierExpression = PsiUtil.skipParenthesizedExprDown(methodExpression.getQualifierExpression());
   if (qualifierExpression instanceof PsiMethodCallExpression expression) {
     return getVariable(expression);
   }
   else if (!(qualifierExpression instanceof PsiReferenceExpression)) {
     return null;
   }
   final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)qualifierExpression;
   final PsiElement target = referenceExpression.resolve();
   if (!(target instanceof PsiVariable)) {
     return null;
   }
   return (PsiVariable)target;
 }
}
