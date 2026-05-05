// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.introduceField;

import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class JavaIntroduceFieldServiceImpl extends JavaIntroduceFieldService {
  private static final FieldExtractor myFieldExtractor = new FieldExtractor(new IntroduceFieldHandler());

  @Override
  public @NotNull JavaIntroduceFieldService.AvailableSettings getAvailableSettings(@NotNull PsiExpression expression) {
    ExpressionToFieldContext ctx = getContext(expression);
    if (!(ctx instanceof ExpressionToFieldContext.Success success)) {
      return new AvailableSettings(List.of());
    }
    return myFieldExtractor.getAvailableSettings(success);
  }

  @Override
  public @NotNull JavaIntroduceFieldService.ExpressionToFieldContext getContext(@NotNull PsiExpression expression) {
    return myFieldExtractor.getContext(expression);
  }

  @Override
  public @Nullable PsiField introduceField(@NotNull PsiExpression expression,
                                           @NotNull JavaIntroduceFieldService.InitializationPlace place) {
    return myFieldExtractor.extractField(expression, place);
  }
}
