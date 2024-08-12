// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.Result;
import com.intellij.codeInsight.template.TextResult;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.PatternSyntaxException;

public final class RegExMacro extends MacroBase {
  private static final Logger LOG = Logger.getInstance(RegExMacro.class);

  public RegExMacro() {
    super("regularExpression", "regularExpression(String, Pattern, Replacement)");
  }

  @Override
  protected @Nullable Result calculateResult(Expression @NotNull [] params, ExpressionContext context, boolean quick) {
    if (params.length != 3) {
      return null;
    }
    Result value = params[0].calculateResult(context);
    if (value == null) {
      return null;
    }
    Result pattern = params[1].calculateResult(context);
    if (pattern == null) {
      return null;
    }
    Result replacement = params[2].calculateResult(context);
    if (replacement == null) {
      return null;
    }
    try {
      return new TextResult(value.toString().replaceAll(pattern.toString(), replacement.toString()));
    } catch (IndexOutOfBoundsException e) {
      LOG.warn("Incorrect replacement value specified in Live Template '" + getName() + "' regularExpression() expression");
    } catch (PatternSyntaxException e) {
      LOG.warn("Incorrect regex specified in Live Template '" + getName() + "' regularExpression() expression");
    }
    return null;
  }
}
