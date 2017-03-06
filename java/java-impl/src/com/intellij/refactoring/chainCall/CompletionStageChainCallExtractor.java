/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.refactoring.chainCall;

import com.intellij.codeInspection.util.OptionalUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Tagir Valeev
 */
public class CompletionStageChainCallExtractor implements ChainCallExtractor {
  @Override
  public boolean canExtractChainCall(@NotNull PsiMethodCallExpression call, PsiExpression expression, PsiType expressionType) {
    if (expressionType instanceof PsiPrimitiveType) return false;
    String methodName = call.getMethodExpression().getReferenceName();
    if (!"thenApply".equals(methodName) && !"thenAccept".equals(methodName) && !"thenCompose".equals(methodName)) {
      return false;
    }
    if (call.getMethodExpression().getQualifierExpression() == null) return false;
    PsiMethod method = call.resolveMethod();
    return method != null &&
           method.getParameterList().getParametersCount() == 1 &&
           InheritanceUtil.isInheritor(method.getContainingClass(), "java.util.concurrent.CompletionStage");
  }

  @Override
  public String buildChainCall(PsiVariable variable, PsiExpression expression, PsiType expressionType) {
    if(expression instanceof PsiArrayInitializerExpression) {
      expression = RefactoringUtil.convertInitializerToNormalExpression(expression, expressionType);
    }
    String typeArgument = OptionalUtil.getMapTypeArgument(expression, expressionType);
    return "." + typeArgument + "thenApply" +
           "(" + variable.getName() + "->" + expression.getText() + ")";
  }
}
