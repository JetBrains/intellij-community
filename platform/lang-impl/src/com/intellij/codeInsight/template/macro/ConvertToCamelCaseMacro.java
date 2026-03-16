// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.Result;
import com.intellij.codeInsight.template.TextResult;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.text.NameUtilCore;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.Arrays;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
@ApiStatus.Internal
public class ConvertToCamelCaseMacro extends MacroBase {

  public ConvertToCamelCaseMacro() {
    super("camelCase", "camelCase(String)");
  }

  private ConvertToCamelCaseMacro(String name, String description) {
    super(name, description);
  }

  @Override
  protected @Nullable Result calculateResult(Expression @NotNull [] params, ExpressionContext context, boolean quick) {
    final String text = getTextResult(params, context, true);
    return text != null ? convertString(text) : null;
  }

  @VisibleForTesting
  public @NotNull Result convertString(@NotNull String text) {
    String result = StreamEx.of(splitWords(text))
      .mapFirstOrElse(StringUtil::toLowerCase, s ->
        Character.isLetterOrDigit(s.charAt(0)) ? StringUtil.capitalize(StringUtil.toLowerCase(s)) : "")
      .joining();
    return new TextResult(result);
  }

  protected List<@NotNull String> splitWords(String text) {
    return NameUtilCore.nameToWordList(text);
  }

  public static final class ReplaceUnderscoresToCamelCaseMacro extends ConvertToCamelCaseMacro {
    public ReplaceUnderscoresToCamelCaseMacro() {
      super("underscoresToCamelCase", CodeInsightBundle.message("macro.underscoresToCamelCase.string"));
    }

    @Override
    protected List<@NotNull String> splitWords(String text) {
      return Arrays.asList(text.split("_"));
    }
  }
}
