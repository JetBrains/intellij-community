/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight.intention.impl;

import com.intellij.psi.LambdaUtil;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiMethodReferenceExpression;
import com.intellij.refactoring.util.LambdaRefactoringUtil;

/**
 * @author Tagir Valeev
 */
public class StreamRefactoringUtil {
  static boolean isRefactoringCandidate(PsiExpression expression, boolean requireExpressionLambda) {
    if(expression instanceof PsiLambdaExpression) {
      PsiLambdaExpression lambdaExpression = (PsiLambdaExpression)expression;
      return lambdaExpression.getParameterList().getParametersCount() == 1 &&
             (!requireExpressionLambda || LambdaUtil.extractSingleExpressionFromBody(lambdaExpression.getBody()) != null);
    } else if(expression instanceof PsiMethodReferenceExpression) {
      return LambdaRefactoringUtil.canConvertToLambdaWithoutSideEffects((PsiMethodReferenceExpression)expression);
    }
    return false;
  }
}
