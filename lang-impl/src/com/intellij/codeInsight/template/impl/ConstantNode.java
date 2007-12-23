package com.intellij.codeInsight.template.impl;

import com.intellij.openapi.editor.*;
import java.util.*;
import com.intellij.openapi.project.Project;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.template.*;

/**
 *
 */
class ConstantNode implements Expression {
  private Result myValue;

  public ConstantNode(String value) {
    myValue = new TextResult(value);
  }

  public Result calculateResult(ExpressionContext context) {
    return myValue;
  }

  public Result calculateQuickResult(ExpressionContext context) {
    return myValue;
  }

  public LookupItem[] calculateLookupItems(ExpressionContext context) {
    return LookupItem.EMPTY_ARRAY;
  }

}
