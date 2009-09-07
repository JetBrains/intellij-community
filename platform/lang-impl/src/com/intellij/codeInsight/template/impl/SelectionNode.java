package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.lookup.LookupElement;
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
    return LookupElement.EMPTY_ARRAY;
  }

  public Result calculateQuickResult(ExpressionContext context) {
    final String selection = context.getProperty(ExpressionContext.SELECTION);
    return new TextResult(selection == null ? "" : selection);
  }

  public Result calculateResult(ExpressionContext context) {
    return calculateQuickResult(context);
  }

}
