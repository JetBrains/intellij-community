package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.Result;
import com.intellij.codeInsight.template.TextResult;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim
 * Date: Nov 7, 2006
 * Time: 12:17:39 AM
 * To change this template use File | Settings | File Templates.
 */
public class TextExpression extends Expression {
  private final String myString;

  public TextExpression(String string) { myString = string; }

  public Result calculateResult(ExpressionContext expressionContext) {
    return new TextResult(myString);
  }

  public Result calculateQuickResult(ExpressionContext expressionContext) {
    return calculateResult(expressionContext);
  }

  public LookupElement[] calculateLookupItems(ExpressionContext expressionContext) {
    return new LookupItem[0];
  }
}
