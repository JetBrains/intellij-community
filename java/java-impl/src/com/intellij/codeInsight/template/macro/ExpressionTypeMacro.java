// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.template.*;
import com.intellij.java.JavaBundle;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ExpressionTypeMacro extends Macro {
  @Override
  public String getName() {
    return "expressionType";
  }

  @Override
  public String getPresentableName() {
    return JavaBundle.message("macro.expression.type");
  }

  @Override
  public @Nullable Result calculateResult(Expression @NotNull [] params, ExpressionContext context) {
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
