package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.Macro;
import com.intellij.codeInsight.template.Result;

import java.util.ArrayList;

/**
 *
 */
public class MacroCallNode implements Expression {
  public Macro getMacro() {
    return myMacro;
  }

  private Macro myMacro;
  private ArrayList<Expression> myParameters = new ArrayList<Expression>();

  public MacroCallNode(Macro macro) {
    myMacro = macro;
  }

  public void addParameter(Expression node) {
    myParameters.add(node);
  }

  public Result calculateResult(ExpressionContext context) {
    Expression[] parameters = myParameters.toArray(new Expression[myParameters.size()]);
    return myMacro.calculateResult(parameters, context);
  }

  public Result calculateQuickResult(ExpressionContext context) {
    Expression[] parameters = myParameters.toArray(new Expression[myParameters.size()]);
    return myMacro.calculateQuickResult(parameters, context);
  }

  public LookupItem[] calculateLookupItems(ExpressionContext context) {
    Expression[] parameters = myParameters.toArray(new Expression[myParameters.size()]);
    return myMacro.calculateLookupItems(parameters, context);
  }

}
