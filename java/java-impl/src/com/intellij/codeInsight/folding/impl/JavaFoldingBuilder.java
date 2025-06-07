// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.folding.impl;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiNewExpression;
import org.jetbrains.annotations.NotNull;

public final class JavaFoldingBuilder extends IdeJavaFoldingBuilderBase {
  @Override
  protected boolean shouldShowExplicitLambdaType(@NotNull PsiAnonymousClass anonymousClass, @NotNull PsiNewExpression expression) {
    if (super.shouldShowExplicitLambdaType(anonymousClass, expression)) {
      return true;
    }
    ExpectedTypeInfo[] types = ExpectedTypesProvider.getExpectedTypes(expression, false);
    return types.length != 1 || !types[0].getType().equals(anonymousClass.getBaseClassType());
  }
}

