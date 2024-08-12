// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.template.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
public abstract class MacroBase extends Macro {
  private final String myName;
  private final String myDescription;

  public MacroBase(String name, String description) {
    myName = name;
    myDescription = description;
  }

  protected abstract @Nullable Result calculateResult(Expression @NotNull [] params, ExpressionContext context, boolean quick);

  @Override
  public Result calculateResult(Expression @NotNull [] params, ExpressionContext context) {
    return calculateResult(params, context, false);
  }

  @Override
  public Result calculateQuickResult(Expression @NotNull [] params, ExpressionContext context) {
    return calculateResult(params, context, true);
  }

  @Override
  public String getName() {
    return myName;
  }

  @Override
  public String getPresentableName() {
    return myDescription;
  }

  @Override
  public @NotNull String getDefaultValue() {
    return "a";
  }

  public static @Nullable String getTextResult(Expression @NotNull [] params, final ExpressionContext context) {
    return getTextResult(params, context, false);
  }

  public static @Nullable String getTextResult(Expression @NotNull [] params, final ExpressionContext context, boolean useSelection) {
    if (params.length == 1) {
      Result result = params[0].calculateResult(context);
      if (result == null && useSelection) {
        final String property = context.getProperty(ExpressionContext.SELECTION);
        if (property != null) {
          result = new TextResult(property);
        }
      }
      return result == null ? null : result.toString();
    }
    return null;
  }
}
