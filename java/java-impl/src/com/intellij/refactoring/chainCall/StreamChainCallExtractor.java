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

import com.intellij.codeInsight.intention.impl.InlineStreamMapAction;
import com.intellij.codeInsight.intention.impl.StreamRefactoringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.siyeh.ig.psiutils.StreamApiUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * @author Tagir Valeev
 */
public class StreamChainCallExtractor implements ChainCallExtractor {
  @Override
  public boolean canExtractChainCall(@NotNull PsiMethodCallExpression call, PsiExpression expression, PsiType expressionType) {
    if (!StreamApiUtil.isSupportedStreamElement(expressionType) ||
        !InlineStreamMapAction.NEXT_METHODS.contains(call.getMethodExpression().getReferenceName()) ||
        call.getMethodExpression().getQualifierExpression() == null) {
      return false;
    }
    PsiMethod method = call.resolveMethod();
    if (method == null ||
        method.getParameterList().getParametersCount() != 1 ||
        !InheritanceUtil.isInheritor(method.getContainingClass(), CommonClassNames.JAVA_UTIL_STREAM_BASE_STREAM)) {
      return false;
    }
    if (method.getName().startsWith("flatMap")) {
      PsiType outType = StreamApiUtil.getStreamElementType(call.getType());
      // flatMap from primitive type works only if the stream element type matches
      if (expressionType instanceof PsiPrimitiveType && !expressionType.equals(outType)) return false;
    }
    return true;
  }

  @Override
  public String fixCallName(PsiMethodCallExpression call, PsiType inType) {
    PsiType outType = StreamApiUtil.getStreamElementType(call.getType(), false);
    String methodName = Objects.requireNonNull(call.getMethodExpression().getReferenceName());
    if (methodName.startsWith("flatMap")) {
      return Objects.requireNonNull(StreamRefactoringUtil.getFlatMapOperationName(inType, outType));
    }
    if (methodName.startsWith("map")) {
      return StreamRefactoringUtil.getMapOperationName(inType, outType);
    }
    return methodName;
  }

  @Override
  public String getMethodName(PsiVariable variable, PsiExpression expression, PsiType expressionType) {
    String shortcutMappingMethod = StreamRefactoringUtil.getShortcutMappingMethod(variable, expressionType, expression);
    if(shortcutMappingMethod != null) return shortcutMappingMethod;
    return StreamRefactoringUtil.getMapOperationName(variable.getType(), expressionType);
  }

  @Override
  public String buildChainCall(PsiVariable variable, PsiExpression expression, PsiType expressionType) {
    return StreamRefactoringUtil.generateMapOperation(variable, expressionType, expression);
  }
}
