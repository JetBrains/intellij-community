// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;

final class CurrentTimeMacro extends SimpleMacro {
  private CurrentTimeMacro() {
    super("time");
  }

  @Override
  protected String evaluateSimpleMacro(Expression[] params, final ExpressionContext context) {
    return CurrentDateMacro.formatUserDefined(params, context, false);
  }
}