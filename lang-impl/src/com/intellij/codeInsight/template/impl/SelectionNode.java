package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.template.*;
import com.intellij.codeInsight.lookup.LookupItem;

/**
 * @author mike
 */
public class SelectionNode implements Expression {
  public SelectionNode() {
  }

  public LookupItem[] calculateLookupItems(ExpressionContext context) {
    return LookupItem.EMPTY_ARRAY;
  }

  public Result calculateQuickResult(ExpressionContext context) {
    return new TextResult((String)context.getProperties().get(ExpressionContext.SELECTION));
  }

  public Result calculateResult(ExpressionContext context) {
    return calculateQuickResult(context);
  }

}
