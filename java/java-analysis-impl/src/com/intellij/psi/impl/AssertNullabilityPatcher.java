// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl;

import com.intellij.codeInsight.JavaExpressionTypeNullabilityPatcher;
import com.intellij.codeInsight.TypeNullability;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiType;
import com.siyeh.ig.callMatcher.CallMatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class AssertNullabilityPatcher implements JavaExpressionTypeNullabilityPatcher {
  private static final CallMatcher NOT_NULL_ASSERTION = CallMatcher.instanceCall(
      "org.assertj.core.api.AbstractAssert", "isNotNull")
    .parameterCount(0);

  @Override
  public @Nullable PsiType tryPatchType(@NotNull PsiExpression expression, @NotNull PsiType type) {
    if (type instanceof PsiClassType classType &&
        expression instanceof PsiMethodCallExpression call &&
        NOT_NULL_ASSERTION.test(call)) {
      PsiClass psiClass = classType.resolve();
      if (psiClass != null) {
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
