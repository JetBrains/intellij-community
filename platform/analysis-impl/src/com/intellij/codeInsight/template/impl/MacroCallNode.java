// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupFocusDegree;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.Macro;
import com.intellij.codeInsight.template.Result;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class MacroCallNode extends Expression {
  private final Macro myMacro;
  private final ArrayList<Expression> myParameters = new ArrayList<>();

  public MacroCallNode(@NotNull Macro macro) {
    myMacro = macro;
  }

  public void addParameter(Expression node) {
    myParameters.add(node);
  }

  public Macro getMacro() {
    return myMacro;
  }

  @Override
  public Result calculateResult(ExpressionContext context) {
    Expression[] parameters = myParameters.toArray(new Expression[0]);
    return getMacro().calculateResult(parameters, context);
  }

  @Override
  public LookupElement[] calculateLookupItems(ExpressionContext context) {
    Expression[] parameters = myParameters.toArray(new Expression[0]);
    return getMacro().calculateLookupItems(parameters, context);
  }

  public Expression[] getParameters() {
    return myParameters.toArray(new Expression[0]);
  }

  @NotNull
  @Override
  public LookupFocusDegree getLookupFocusDegree() {
    return getMacro().getLookupFocusDegree();
  }
}
