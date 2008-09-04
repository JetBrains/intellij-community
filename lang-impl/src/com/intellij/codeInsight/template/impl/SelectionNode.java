package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.Result;
import com.intellij.codeInsight.template.TextResult;

/**
 * @author mike
 */
public class SelectionNode extends Expression {
  public SelectionNode() {
  }

  public LookupElement[] calculateLookupItems(ExpressionContext context) {
    return LookupItem.EMPTY_ARRAY;
  }

  public Result calculateQuickResult(ExpressionContext context) {
    return new TextResult((String)context.getProperties().get(ExpressionContext.SELECTION));
  }

  public Result calculateResult(ExpressionContext context) {
    return calculateQuickResult(context);
  }

}
