/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.codeInsight.template.*;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ExpressionTypeMacro extends Macro {
  @Override
  public String getName() {
    return "expressionType";
  }

  @Override
  public String getPresentableName() {
    return CodeInsightBundle.message("macro.expression.type");
  }

  @Nullable
  @Override
  public Result calculateResult(@NotNull Expression[] params, ExpressionContext context) {
    if (params.length == 1) {
      Result result = params[0].calculateResult(context);
      if (result != null) {
        PsiExpression expression = MacroUtil.resultToPsiExpression(result, context);
        if (expression != null) {
          PsiType type = expression.getType();
          if (type != null) {
            return new PsiTypeResult(type, context.getProject());
          }
        }
      }
    }

    return null;
  }
}
