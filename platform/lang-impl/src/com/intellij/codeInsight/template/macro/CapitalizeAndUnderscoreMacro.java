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
public class CapitalizeAndUnderscoreMacro extends MacroBase {
  public CapitalizeAndUnderscoreMacro() {
    super("capitalizeAndUnderscore", CodeInsightBundle.message("macro.capitalizeAndUnderscore.string"));
  }

  protected CapitalizeAndUnderscoreMacro(String name, String description) {
    super(name, description);
  }

  @Override
  protected Result calculateResult(@NotNull Expression[] params, ExpressionContext context, boolean quick) {
    String text = getTextResult(params, context, true);
    if (text != null && text.length() > 0) {
      final String[] words = NameUtil.nameToWords(text);
      boolean insertUnderscore = false;
      final StringBuilder buf = new StringBuilder();
      for (String word : words) {
        if (insertUnderscore) {
          buf.append("_");
        } else {
          insertUnderscore = true;
        }
        buf.append(convertCase(word));
      }
      return new TextResult(buf.toString());
    }
    return null;
  }

  protected String convertCase(String word) {
    return StringUtil.toUpperCase(word);
  }
}
