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
package com.siyeh.ipp.junit;

import com.intellij.psi.*;
import com.siyeh.ig.junit.JUnitCommonClassNames;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ErrorUtil;
import org.jetbrains.annotations.NonNls;

class AssertTrueOrFalsePredicate implements PsiElementPredicate {

  @Override
  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiMethodCallExpression expression)) {
      return false;
    }
    final PsiExpressionList argumentList = expression.getArgumentList();
    final int numExpressions = argumentList.getExpressionCount();
    if (numExpressions < 1 || numExpressions > 2) {
      return false;
    }
    final PsiReferenceExpression methodExpression = expression.getMethodExpression();
    @NonNls final String methodName = methodExpression.getReferenceName();
    if (!"assertTrue".equals(methodName) && !"assertFalse".equals(methodName)) {
      return false;
    }
    final PsiMethod method = expression.resolveMethod();
    if (method == null) {
      return false;
    }
    final PsiClass targetClass = method.getContainingClass();
    if (targetClass == null) {
      return false;
    }
    final String qualifiedName = targetClass.getQualifiedName();
    if (!JUnitCommonClassNames.JUNIT_FRAMEWORK_ASSERT.equals(qualifiedName) &&
        !JUnitCommonClassNames.ORG_JUNIT_ASSERT.equals(qualifiedName) &&
        !JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_ASSERTIONS.equals(qualifiedName)) {
      return false;
    }
    return !ErrorUtil.containsError(element);
  }
}