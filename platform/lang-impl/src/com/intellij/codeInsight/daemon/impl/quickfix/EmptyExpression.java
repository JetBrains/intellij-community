// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.Result;

public class EmptyExpression extends Expression {
  public EmptyExpression() {
  }

  @Override
  public Result calculateResult(ExpressionContext context) {
    return null;
  }

  @Override
  public boolean requiresCommittedPSI() {
    return false;
  }

  @Override
  public LookupElement[] calculateLookupItems(ExpressionContext context) {
    return null;
  }

}