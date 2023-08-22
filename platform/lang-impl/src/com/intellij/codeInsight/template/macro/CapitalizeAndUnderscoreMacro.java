// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.Result;
import com.intellij.codeInsight.template.TextResult;
import com.intellij.psi.codeStyle.NameUtil;
import org.jetbrains.annotations.NotNull;

public final class CapitalizeAndUnderscoreMacro extends MacroBase {

  public CapitalizeAndUnderscoreMacro() {
    super("capitalizeAndUnderscore", CodeInsightBundle.message("macro.capitalizeAndUnderscore.string"));
  }

  @Override
  protected Result calculateResult(Expression @NotNull [] params, ExpressionContext context, boolean quick) {
    String text = getTextResult(params, context, true);
    return text != null ? new TextResult(!text.isEmpty() ? NameUtil.capitalizeAndUnderscore(text) : "") : null;
  }
}
