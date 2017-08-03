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

import com.intellij.codeInsight.intention.impl.StreamRefactoringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.siyeh.ig.psiutils.StreamApiUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Tagir Valeev
 */
public class CollectionChainCallExtractor implements ChainCallExtractor {
  @Override
  public boolean canExtractChainCall(@NotNull PsiMethodCallExpression call, PsiExpression expression, PsiType expressionType) {
    PsiReferenceExpression methodExpression = call.getMethodExpression();
    if (!StreamApiUtil.isSupportedStreamElement(expressionType) ||
        !"forEach".equals(methodExpression.getReferenceName()) ||
        methodExpression.getQualifierExpression() == null ||
        !InheritanceUtil.isInheritor(methodExpression.getQualifierExpression().getType(), CommonClassNames.JAVA_UTIL_COLLECTION)) {
      return false;
    }
    PsiMethod method = call.resolveMethod();
    return method != null && method.getParameterList().getParametersCount() == 1;
  }

  @Override
  public String getMethodName(PsiVariable variable, PsiExpression expression, PsiType expressionType) {
    return "stream()." + StreamRefactoringUtil.getMapOperationName(variable.getType(), expressionType);
  }

  @Override
  public String buildChainCall(PsiVariable variable, PsiExpression expression, PsiType expressionType) {
    return ".stream()" + StreamRefactoringUtil.generateMapOperation(variable, expressionType, expression);
  }
}
