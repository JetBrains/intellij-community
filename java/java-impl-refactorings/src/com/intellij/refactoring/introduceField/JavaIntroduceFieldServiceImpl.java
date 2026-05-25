// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.introduceField;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class JavaIntroduceFieldServiceImpl extends JavaIntroduceFieldService {
  private static final FieldExtractor myFieldExtractor = new FieldExtractor(new IntroduceFieldHelper());
  private static final FieldExtractor myConstantExtractor = new FieldExtractor(new IntroduceConstantHelper());

  @Override
  public @NotNull JavaIntroduceFieldService.AvailableSettings getAvailableSettings(@NotNull PsiExpression expression) {
    ToFieldContext ctx = myFieldExtractor.getContext(expression.getContainingFile(), expression);
    if (!(ctx instanceof ToFieldContext.ExpressionContext success)) {
      return new AvailableSettings(List.of());
    }
    return getAvailableSettings(success);
  }

  @Override
  public @NotNull JavaIntroduceFieldService.AvailableSettings getAvailableSettings(@NotNull ToFieldContext.ExpressionContext context) {
    return myFieldExtractor.getAvailableSettings(context);
  }

  @Override
  public @NotNull JavaIntroduceFieldService.AvailableSettings getAvailableSettings(@NotNull ToFieldContext.VariableContext context) {
    return myFieldExtractor.getAvailableSettings(context);
  }

  @Override
  public @NotNull JavaIntroduceFieldService.ToFieldContext getContext(@NotNull PsiFile psiFile,
                                                                      @NotNull TextRange range,
                                                                      boolean isConstant) {
    if (isConstant) {
      return myConstantExtractor.getContext(psiFile, range);
    }
    return myFieldExtractor.getContext(psiFile, range);
  }

  @Override
  public @Nullable PsiField introduceField(@NotNull PsiExpression expression,
                                           @NotNull JavaIntroduceFieldService.InitializationPlace place) {
    return myFieldExtractor.extractField((PsiJavaFile)expression.getContainingFile(), expression.getTextRange(), place);
  }

  @Override
  public @Nullable PsiField introduceField(@NotNull PsiJavaFile psiJavaFile,
                                           @NotNull TextRange range,
                                           boolean isConstant,
                                           @NotNull JavaIntroduceFieldService.InitializationPlace place) {
    if (isConstant) {
      return myConstantExtractor.extractField(psiJavaFile, range, place);
    }
    return myFieldExtractor.extractField(psiJavaFile, range, place);
  }
}
