package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.template.Expression;

public class Variable implements Cloneable {
  private String myName;
  private boolean myAlwaysStopAt;

  private String myExpressionString;
  private Expression myExpression = null;

  private String myDefaultValueString;
  private Expression myDefaultValueExpression;

  public Variable(String name, Expression expression, Expression defaultValueExpression, boolean alwaysStopAt) {
    myName = name;
    myExpression = expression;
    myDefaultValueExpression = defaultValueExpression;
    myAlwaysStopAt = alwaysStopAt;
  }

  public Variable(String name, String expression, String defaultValueString, boolean alwaysStopAt) {
    myName = name;
    myExpressionString = expression;
    myDefaultValueString = defaultValueString;
    myAlwaysStopAt = alwaysStopAt;
  }

  public String getExpressionString() {
    return myExpressionString;
  }

  public void setExpressionString(String expressionString) {
    myExpressionString = expressionString;
    myExpression = null;
  }

  public Expression getExpression() {
    if (myExpression == null) {
      if (myName.equals(TemplateImpl.SELECTION)) {
        myExpression = new SelectionNode();
      }
      else {
        myExpression = MacroParser.parse(myExpressionString);
      }
    }
    return myExpression;
  }

  public String getDefaultValueString() {
    return myDefaultValueString;
  }

  public void setDefaultValueString(String defaultValueString) {
    myDefaultValueString = defaultValueString;
    myDefaultValueExpression = null;
  }

  public Expression getDefaultValueExpression() {
    if (myDefaultValueExpression == null) {
      myDefaultValueExpression = MacroParser.parse(myDefaultValueString);
    }
    return myDefaultValueExpression;
  }

  public String getName() {
    return myName;
  }

  public boolean isAlwaysStopAt() {
    if (myName.equals(TemplateImpl.SELECTION)) return false;
    return myAlwaysStopAt;
  }

  public void setAlwaysStopAt(boolean alwaysStopAt) {
    myAlwaysStopAt = alwaysStopAt;
  }

  public Object clone() {
    return new Variable(myName, myExpressionString, myDefaultValueString, myAlwaysStopAt);
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Variable)) return false;

    final Variable variable = (Variable) o;

    if (myAlwaysStopAt != variable.myAlwaysStopAt) return false;
    if (myDefaultValueString != null ? !myDefaultValueString.equals(variable.myDefaultValueString) : variable.myDefaultValueString != null) return false;
    if (myExpressionString != null ? !myExpressionString.equals(variable.myExpressionString) : variable.myExpressionString != null) return false;
    if (myName != null ? !myName.equals(variable.myName) : variable.myName != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (myName != null ? myName.hashCode() : 0);
    result = 29 * result + (myAlwaysStopAt ? 1 : 0);
    result = 29 * result + (myExpressionString != null ? myExpressionString.hashCode() : 0);
    result = 29 * result + (myDefaultValueString != null ? myDefaultValueString.hashCode() : 0);
    return result;
  }
}
