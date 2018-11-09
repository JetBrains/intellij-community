// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.Result;
import com.intellij.codeInsight.template.TextResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RegExMacro extends MacroBase {

  public RegExMacro() {
    super("regularExpression", "regularExpression(String, Pattern, Replacement)");
  }

  @Nullable
  @Override
  protected Result calculateResult(@NotNull Expression[] params, ExpressionContext context, boolean quick) {
    String value = getTextResult(params[0], context);
    if (value == null) {
      return null;
    }
    String pattern = getTextResult(params[1], context);
    if (pattern == null) {
      return null;
    }
    String matcher = getTextResult(params[2], context);
    if (matcher == null) {
      return null;
    }
    return new TextResult(value.replaceAll(pattern, matcher));
  }

  @Nullable
  private static String getTextResult(@NotNull Expression param, ExpressionContext context) {
    Result result = param.calculateResult(context);
    return result != null ? result.toString() : null;
  }
}
