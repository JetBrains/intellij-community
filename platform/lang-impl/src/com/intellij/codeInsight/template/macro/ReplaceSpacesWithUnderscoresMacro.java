// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.Result;
import com.intellij.codeInsight.template.TextResult;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class ReplaceSpacesWithUnderscoresMacro extends MacroBase {
  public ReplaceSpacesWithUnderscoresMacro() {
    super("spacesToUnderscores", CodeInsightBundle.message("macro.spacesToUnderscores.string"));
  }

  @Override
  protected Result calculateResult(Expression @NotNull [] params, ExpressionContext context, boolean quick) {
    final String text = getTextResult(params, context);
    if (text != null) {
      return new TextResult(text.replace(' ', '_'));
    }
    return null;
  }
}