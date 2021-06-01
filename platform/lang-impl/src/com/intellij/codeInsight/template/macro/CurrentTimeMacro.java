// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;


public class CurrentTimeMacro extends SimpleMacro {
  protected CurrentTimeMacro() {
    super("time");
  }

  @Override
  protected String evaluateSimpleMacro(Expression[] params, final ExpressionContext context) {
    return CurrentDateMacro.formatUserDefined(params, context, false);
  }
}