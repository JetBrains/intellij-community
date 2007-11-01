package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.Result;

/**
 *
 */
class VariableNode implements Expression {
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

  public LookupItem[] calculateLookupItems(ExpressionContext context) {
    if (myInitialValue == null){
      return null;
    }
    return myInitialValue.calculateLookupItems(context);
  }

  private String getName() {
    return myName;
  }

}
