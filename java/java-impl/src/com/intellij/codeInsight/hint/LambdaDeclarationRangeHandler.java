// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hint;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLambdaExpression;
import org.jetbrains.annotations.NotNull;

public final class LambdaDeclarationRangeHandler implements DeclarationRangeHandler {
  @Override
  public @NotNull TextRange getDeclarationRange(final @NotNull PsiElement container) {
    final PsiLambdaExpression lambdaExpression = (PsiLambdaExpression)container;
    return lambdaExpression.getParameterList().getTextRange();
  }
}
