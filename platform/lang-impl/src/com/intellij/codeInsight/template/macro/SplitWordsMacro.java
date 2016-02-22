/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.template.macro;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.Result;
import com.intellij.codeInsight.template.TextResult;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.NameUtil;
import org.jetbrains.annotations.NotNull;

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
  protected Result calculateResult(@NotNull Expression[] params, ExpressionContext context, boolean quick) {
    String text = getTextResult(params, context, true);
    if (StringUtil.isNotEmpty(text)) {
      return new TextResult(convertString(text));
    }
    return null;
  }

  @VisibleForTesting
  public String convertString(String text) {
    final String[] words = NameUtil.nameToWords(text);
    boolean insertSeparator = false;
    final StringBuilder buf = new StringBuilder();
    for (String word : words) {
      if (!Character.isLetterOrDigit(word.charAt(0))) {
        buf.append(mySeparator);
        insertSeparator = false;
        continue;
      }
      if (insertSeparator) {
        buf.append(mySeparator);
      } else {
        insertSeparator = true;
      }
      buf.append(convertCase(word));
    }
    return buf.toString();
  }

  @NotNull protected abstract String convertCase(@NotNull String word);

  public static class CapitalizeAndUnderscoreMacro extends SplitWordsMacro {

    public CapitalizeAndUnderscoreMacro() {
      super("capitalizeAndUnderscore", CodeInsightBundle.message("macro.capitalizeAndUnderscore.string"), '_');
    }

    @NotNull
    protected String convertCase(@NotNull String word) {
      return StringUtil.toUpperCase(word);
    }
  }

  public static class SnakeCaseMacro extends SplitWordsMacro {
    public SnakeCaseMacro() {
      super("snakeCase", "snakeCase(String)", '_');
    }

    @NotNull
    @Override
    protected String convertCase(@NotNull String word) {
      //noinspection StringToUpperCaseOrToLowerCaseWithoutLocale
      return word.toLowerCase();
    }
  }

  public static class LowercaseAndDash extends SplitWordsMacro {
    public LowercaseAndDash() {
      super("lowercaseAndDash", "lowercaseAndDash(String)", '-');
    }

    @NotNull
    @Override
    protected String convertCase(@NotNull String word) {
      //noinspection StringToUpperCaseOrToLowerCaseWithoutLocale
      return word.toLowerCase();
    }
  }

  public static class SpaceSeparated extends SplitWordsMacro {
    public SpaceSeparated() {
      super("spaceSeparated", "spaceSeparated(String)", ' ');
    }

    @NotNull
    @Override
    protected String convertCase(@NotNull String word) {
      return word;
    }
  }
}
