// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.template.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;


public abstract class SimpleMacro extends Macro {
  private final String myName;

  protected SimpleMacro(final String name) {
    myName = name;
  }

  @Override
  public @NonNls String getName() {
    return myName;
  }

  @Override
  public @NotNull @NonNls String getDefaultValue() {
    return "11.11.1111";
  }

  @Override
  public Result calculateResult(final Expression @NotNull [] params, final ExpressionContext context) {
    return new TextResult(evaluateSimpleMacro(params, context));
  }

  @Override
  public Result calculateQuickResult(final Expression @NotNull [] params, final ExpressionContext context) {
    return calculateResult(params, context);
  }

  protected abstract String evaluateSimpleMacro(Expression[] params, final ExpressionContext context);
}
