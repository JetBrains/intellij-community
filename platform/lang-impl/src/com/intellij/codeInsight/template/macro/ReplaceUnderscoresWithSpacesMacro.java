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
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Roman.Chernyatchik
 */
public class ReplaceUnderscoresWithSpacesMacro implements Macro {
  @NonNls
  public String getName() {
    return "underscoresToSpaces";
  }

  public String getDescription() {
    return CodeInsightBundle.message("macro.undescoresToSpaces.string");
  }

  @NonNls
  public String getDefaultValue() {
    return "a";
  }

  public Result calculateResult(@NotNull Expression[] params, ExpressionContext context) {
    if (params.length != 1) {
      return null;
    }
    Result param_result = params[0].calculateResult(context);
    if (param_result == null) {
      return null;
    }
    return execute(param_result);
  }

  public Result calculateQuickResult(@NotNull Expression[] params, ExpressionContext context) {
    return calculateResult(params, context);
  }

  public LookupElement[] calculateLookupItems(@NotNull Expression[] params, ExpressionContext context) {
    return LookupElement.EMPTY_ARRAY;
  }

  private Result execute(final Result param_result) {
    final String param_string_value = param_result.toString();
    return new TextResult(param_string_value.replace('_', ' '));
  }
}