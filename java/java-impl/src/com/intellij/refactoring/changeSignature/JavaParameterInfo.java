// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.changeSignature;

import com.intellij.psi.*;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Maxim.Medvedev
 */
public interface JavaParameterInfo extends ParameterInfo {
  @Nullable
  PsiType createType(@Nullable PsiElement context, final PsiManager manager) throws IncorrectOperationException;

  default @Nullable PsiType createType(@NotNull PsiElement context) {
    return createType(context, context.getManager());
  }

  @Override
  String getTypeText();

  CanonicalTypes.Type getTypeWrapper();

  PsiExpression getValue(PsiCallExpression callExpression);

  @Override
  default @Nullable PsiElement getActualValue(PsiElement callExpression, Object substitutor) {
    return callExpression instanceof PsiCallExpression ? getValue((PsiCallExpression)callExpression) : null;
  }

  boolean isVarargType();

  default void setType(@Nullable PsiType type) {}
}
