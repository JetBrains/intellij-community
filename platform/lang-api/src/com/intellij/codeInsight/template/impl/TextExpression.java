// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.Result;
import com.intellij.codeInsight.template.TextResult;


public class TextExpression extends Expression {
  private final String myString;

  public TextExpression(String string) { myString = string; }

  @Override
  public Result calculateResult(ExpressionContext expressionContext) {
    return new TextResult(myString);
  }

  @Override
  public boolean requiresCommittedPSI() {
    return false;
  }

  @Override
  public LookupElement[] calculateLookupItems(ExpressionContext expressionContext) {
    return LookupElement.EMPTY_ARRAY;
  }
}
