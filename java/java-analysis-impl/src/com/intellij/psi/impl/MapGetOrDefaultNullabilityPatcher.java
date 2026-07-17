// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl;

import com.intellij.codeInsight.JavaExpressionTypeNullabilityPatcher;
import com.intellij.codeInsight.Nullability;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiType;
import com.siyeh.ig.callMatcher.CallMatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class MapGetOrDefaultNullabilityPatcher implements JavaExpressionTypeNullabilityPatcher {
  private static final CallMatcher MAP_GET_OR_DEFAULT =
    CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_MAP, "getOrDefault").parameterCount(2);

  @Override
  public @Nullable PsiType tryPatchType(@NotNull PsiExpression expression, @NotNull PsiType type) {
    if (!(expression instanceof PsiMethodCallExpression call) ||
        !MAP_GET_OR_DEFAULT.test(call) ||
        type.getNullability().nullability() != Nullability.NOT_NULL) {
      return null;
    }
    PsiExpression defaultValue = call.getArgumentList().getExpressions()[1];
    if(defaultValue == null) return null;
    PsiType defaultValueType = defaultValue.getType();
    return defaultValueType == null ? null : type.withNullability(defaultValueType.getNullability());
  }
}
