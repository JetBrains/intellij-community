// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl;

import com.intellij.codeInsight.JavaExpressionTypeNullabilityPatcher;
import com.intellij.codeInsight.TypeNullability;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiType;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class StreamNullabilityPatcher implements JavaExpressionTypeNullabilityPatcher {
  private static final CallMatcher STREAM_FILTER_TAKE_WHILE = CallMatcher.instanceCall(
      CommonClassNames.JAVA_UTIL_STREAM_STREAM, "filter", "takeWhile")
    .parameterCount(1);

  @Override
  public @Nullable PsiType tryPatchType(@NotNull PsiExpression expression, @NotNull PsiType type) {
    if (type instanceof PsiClassType classType &&
        expression instanceof PsiMethodCallExpression call &&
        STREAM_FILTER_TAKE_WHILE.test(call) && 
        ExpressionUtils.isNullFilteringFunction(call.getArgumentList().getExpressions()[0])) {
      PsiClass psiClass = classType.resolve();
      if (psiClass != null && CommonClassNames.JAVA_UTIL_STREAM_STREAM.equals(psiClass.getQualifiedName())) {
        PsiType[] parameters = classType.getParameters();
        if (parameters.length == 1) {
          return JavaPsiFacade.getElementFactory(expression.getProject())
            .createType(psiClass, parameters[0].withNullability(TypeNullability.NOT_NULL_KNOWN));
        }
      }
    }
    return null;
  }
}
