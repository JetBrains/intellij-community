// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.Result;
import com.intellij.codeInsight.template.TextResult;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ConcatMacro extends MacroBase {
  public ConcatMacro() {
    super("concat", "concat(expressions...)");
  }

  @Override
  protected @Nullable Result calculateResult(Expression @NotNull [] params, ExpressionContext context, boolean quick) {
    StringBuilder result = new StringBuilder();
    for (Expression param : params) {
      Result paramResult = param.calculateResult(context);
      if (paramResult != null) {
        result.append(StringUtil.notNullize(paramResult.toString()));
      }
    }
    return result.length() != 0 ? new TextResult(result.toString()) : null;
  }
}
