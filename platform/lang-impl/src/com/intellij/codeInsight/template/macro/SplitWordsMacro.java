// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.Result;
import com.intellij.codeInsight.template.TextResult;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.NameUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * @author Konstantin Bulenkov
 */
public abstract class SplitWordsMacro extends MacroBase {
  private final char mySeparator;

  private SplitWordsMacro(String name, String description, char separator) {
    super(name, description);
    mySeparator = separator;
  }

  @Override
  protected Result calculateResult(Expression @NotNull [] params, ExpressionContext context, boolean quick) {
    String text = getTextResult(params, context, true);
    return text != null ? new TextResult(!text.isEmpty() ? convertString(text) : "") : null;
  }

  @VisibleForTesting
  public String convertString(String text) {
    return NameUtil.splitWords(text, mySeparator, this::convertCase);
  }

  protected abstract @NotNull String convertCase(@NotNull String word);

  public static final class SnakeCaseMacro extends SplitWordsMacro {
    public SnakeCaseMacro() {
      super("snakeCase", "snakeCase(String)", '_');
    }

    @Override
    protected @NotNull String convertCase(@NotNull String word) {
      return StringUtil.toLowerCase(word);
    }
  }

  public static final class LowercaseAndDash extends SplitWordsMacro {
    public LowercaseAndDash() {
      super("lowercaseAndDash", "lowercaseAndDash(String)", '-');
    }

    @Override
    protected @NotNull String convertCase(@NotNull String word) {
      return StringUtil.toLowerCase(word);
    }
  }

  public static final class SpaceSeparated extends SplitWordsMacro {
    public SpaceSeparated() {
      super("spaceSeparated", "spaceSeparated(String)", ' ');
    }

    @Override
    protected @NotNull String convertCase(@NotNull String word) {
      return word;
    }
  }
}
