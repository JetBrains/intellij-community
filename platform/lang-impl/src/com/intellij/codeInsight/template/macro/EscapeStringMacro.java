// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.Result;
import com.intellij.codeInsight.template.TextResult;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class EscapeStringMacro extends MacroBase {
  public EscapeStringMacro() {
    super("escapeString", "escapeString(String)");
  }

  @Override
  protected Result calculateResult(Expression @NotNull [] params, ExpressionContext context, boolean quick) {
    final String text = getTextResult(params, context);
    return text != null ? new TextResult(StringUtil.escapeStringCharacters(text)) : null;
  }

}
