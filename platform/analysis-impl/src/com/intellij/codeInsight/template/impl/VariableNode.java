// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.Result;
import org.jetbrains.annotations.Nullable;

public class VariableNode extends Expression {
  private final String myName;
  private final Expression myInitialValue;

  public VariableNode(String name, @Nullable Expression initialValue) {
    myName = name;
    myInitialValue = initialValue;
  }

  @Override
  public Result calculateResult(ExpressionContext context) {
    if (myInitialValue != null){
      return myInitialValue.calculateQuickResult(context);
    }
    return TemplateManagerUtilBase.getTemplateState(context.getEditor()).getVariableValue(getName());
  }

  @Override
  public boolean requiresCommittedPSI() {
    return myInitialValue != null && myInitialValue.requiresCommittedPSI();
  }

  @Override
  public LookupElement[] calculateLookupItems(ExpressionContext context) {
    if (myInitialValue == null){
      return null;
    }
    return myInitialValue.calculateLookupItems(context);
  }

  public String getName() {
    return myName;
  }

  @Nullable public Expression getInitialValue() {
    return myInitialValue;
  }
}
