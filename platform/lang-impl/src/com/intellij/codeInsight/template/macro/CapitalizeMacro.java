// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.Result;
import com.intellij.codeInsight.template.TextResult;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
public class CapitalizeMacro extends MacroBase {
  public CapitalizeMacro() {
    super("capitalize", CodeInsightBundle.message("macro.capitalize.string"));
  }

  @Override
  protected Result calculateResult(Expression @NotNull [] params, ExpressionContext context, boolean quick) {
    String text = getTextResult(params, context);
    if (text != null) {
      if (!text.isEmpty()) {
        text = StringUtil.toUpperCase(text.substring(0, 1)) + text.substring(1);
      }
      return new TextResult(text);
    }
    return null;
  }
}
