package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.Result;

/**
 *
 */
public class VariableNode extends Expression {
  private final String myName;
  private final Expression myInitialValue;

  public VariableNode(String name, Expression initialValue) {
    myName = name;
    myInitialValue = initialValue;
  }

  public Result calculateResult(ExpressionContext context) {
    Result ret = null;
    if (myInitialValue != null){
      ret = myInitialValue.calculateResult(context);
    }
    else{
      ret = TemplateManagerImpl.getTemplateState(context.getEditor()).getVariableValue(getName());
    }
    return ret;
  }

  public Result calculateQuickResult(ExpressionContext context) {
    Result ret = null;
    if (myInitialValue != null){
      ret = myInitialValue.calculateQuickResult(context);
    }
    else{
      ret = TemplateManagerImpl.getTemplateState(context.getEditor()).getVariableValue(getName());
    }
    return ret;
  }

  public LookupElement[] calculateLookupItems(ExpressionContext context) {
    if (myInitialValue == null){
      return null;
    }
    return myInitialValue.calculateLookupItems(context);
  }

  public String getName() {
    return myName;
  }

  public Expression getInitialValue() {
    return myInitialValue;
  }
}
