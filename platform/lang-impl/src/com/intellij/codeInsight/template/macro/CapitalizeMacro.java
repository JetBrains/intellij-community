/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.*;
import org.jetbrains.annotations.NotNull;

public class CapitalizeMacro implements Macro {

  public String getName() {
    return "capitalize";
  }

  public String getDescription() {
    return CodeInsightBundle.message("macro.capitalize.string");
  }

  public String getDefaultValue() {
    return "A";
  }

  public Result calculateResult(@NotNull Expression[] params, ExpressionContext context) {
    if (params.length != 1) return null;
    Result result = params[0].calculateResult(context);
    return execute(result);
  }

  public Result calculateQuickResult(@NotNull Expression[] params, ExpressionContext context) {
    if (params.length != 1) return null;
    Result result = params[0].calculateQuickResult(context);
    return execute(result);
  }

  private Result execute(Result result) {
    if (result == null) return null;
    String text = result.toString();
    if (text == null) return null;
    if (text.length() > 0) {
      text = text.substring(0, 1).toUpperCase() + text.substring(1, text.length());
    }
    return new TextResult(text);
  }

  public LookupElement[] calculateLookupItems(@NotNull Expression[] params, final ExpressionContext context) {
    return null;
  }
}
