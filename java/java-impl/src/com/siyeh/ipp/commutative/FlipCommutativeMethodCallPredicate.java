/*
 * Copyright 2003-2019 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.commutative;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ErrorUtil;

class FlipCommutativeMethodCallPredicate implements PsiElementPredicate {

  @Override
  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiMethodCallExpression expression)) {
      return false;
    }
    if (ErrorUtil.containsError(element)) {
      return false;
    }
    // do it only when there is just one argument.
    final PsiExpression[] arguments = expression.getArgumentList().getExpressions();
    if (arguments.length != 1) {
      return false;
    }
    final PsiExpression argument = PsiUtil.skipParenthesizedExprDown(arguments[0]);
    if (argument == null) {
      return false;
    }
    final PsiReferenceExpression methodExpression = expression.getMethodExpression();
    final PsiExpression qualifier = PsiUtil.skipParenthesizedExprDown(methodExpression.getQualifierExpression());
    final PsiType qualifierType;
    if (qualifier == null) {
      if (argument instanceof PsiThisExpression) {
        return false;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null || method.hasModifierProperty(PsiModifier.STATIC)) {
        return false;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return false;
      }
      qualifierType = JavaPsiFacade.getElementFactory(aClass.getProject()).createType(aClass);
    }
    else {
      if (EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(argument, qualifier)) {
        return false;
      }
      qualifierType = qualifier.getType();
      if (!(qualifierType instanceof PsiClassType)) {
        return false;
      }
    }
    final String methodName = methodExpression.getReferenceName();
    final PsiType argumentType = argument.getType();
    if (!(argumentType instanceof PsiClassType)) {
      return false;
    }
    if (qualifierType.equals(argumentType)) {
      return true;
    }
    final PsiClassType.ClassResolveResult resolveResult = ((PsiClassType)argumentType).resolveGenerics();
    final PsiClass argumentClass = resolveResult.getElement();
    if (argumentClass == null) {
      return false;
    }
    final PsiMethod[] methods = argumentClass.findMethodsByName(methodName, true);
    for (final PsiMethod testMethod : methods) {
      final PsiParameterList parameterList = testMethod.getParameterList();
      if (parameterList.getParametersCount() == 1) {
        final PsiParameter parameter = parameterList.getParameters()[0];
        final PsiClass containingClass = testMethod.getContainingClass();
        if (containingClass != null) {
          final PsiSubstitutor substitutor =
            TypeConversionUtil.getClassSubstitutor(containingClass, argumentClass, resolveResult.getSubstitutor());
          if (substitutor != null) {
            final PsiType type = substitutor.substitute(parameter.getType());
            if (type != null && type.isAssignableFrom(qualifierType)) {
              return true;
            }
          }
        }
      }
    }
    return false;
  }
}