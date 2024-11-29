// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.Template;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Variable implements Cloneable {
  private final String myName;
  private boolean myAlwaysStopAt;

  private @Nullable String myExpressionString;
  private Expression myExpression = null;

  private String myDefaultValueString;
  private Expression myDefaultValueExpression;
  private final boolean mySkipOnStart;

  public Variable(@NotNull Variable from) {
    myName = from.myName;
    myExpression = from.myExpression;
    myDefaultValueExpression = from.myDefaultValueExpression;
    myExpressionString = from.myExpressionString;
    myDefaultValueString = from.myDefaultValueString;
    myAlwaysStopAt = from.myAlwaysStopAt;
    mySkipOnStart = from.mySkipOnStart;
  }

  public Variable(@NotNull @NlsSafe String name, @Nullable Expression expression, @Nullable Expression defaultValueExpression,
                  boolean alwaysStopAt, boolean skipOnStart) {
    myName = name;
    myExpression = expression;
    myDefaultValueExpression = defaultValueExpression;
    myAlwaysStopAt = alwaysStopAt;
    mySkipOnStart = skipOnStart;
  }

  public Variable(@NotNull @NlsSafe String name,
                  @Nullable @NlsSafe String expression,
                  @Nullable @NlsSafe String defaultValueString,
                  boolean alwaysStopAt) {
    myName = name;
    myExpressionString = StringUtil.notNullize(expression);
    myDefaultValueString = StringUtil.notNullize(defaultValueString);
    myAlwaysStopAt = alwaysStopAt;
    mySkipOnStart = false;
  }

  public @NotNull String getExpressionString() {
    return StringUtil.notNullize(myExpressionString);
  }

  public void setExpressionString(@Nullable String expressionString) {
    myExpressionString = expressionString;
    myExpression = null;
  }

  public @NotNull Expression getExpression() {
    if (myExpression == null) {
      if (myName.equals(Template.SELECTION)) {
        myExpression = new SelectionNode();
      }
      else {
        myExpression = MacroParser.parse(myExpressionString);
      }
    }
    return myExpression;
  }

  public @NotNull String getDefaultValueString() {
    return StringUtil.notNullize(myDefaultValueString);
  }

  public void setDefaultValueString(@Nullable String defaultValueString) {
    myDefaultValueString = defaultValueString;
    myDefaultValueExpression = null;
  }

  public @NotNull Expression getDefaultValueExpression() {
    if (myDefaultValueExpression == null) {
      myDefaultValueExpression = MacroParser.parse(myDefaultValueString);
    }
    return myDefaultValueExpression;
  }

  void dropParsedData() {
    if (myExpressionString != null) {
      myExpression = null;
    }
    if (myDefaultValueString != null) {
      myDefaultValueExpression = null;
    }
  }

  public @NotNull String getName() {
    return myName;
  }

  public boolean isAlwaysStopAt() {
    if (myName.equals(Template.SELECTION)) return false;
    return myAlwaysStopAt;
  }

  public void setAlwaysStopAt(boolean alwaysStopAt) {
    myAlwaysStopAt = alwaysStopAt;
  }

  @Override
  public Object clone() {
    return new Variable(myName, myExpressionString, myDefaultValueString, myAlwaysStopAt);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Variable variable)) return false;

    if (myAlwaysStopAt != variable.myAlwaysStopAt) return false;
    if (mySkipOnStart != variable.mySkipOnStart) return false;
    if (myDefaultValueString != null ? !myDefaultValueString.equals(variable.myDefaultValueString) : variable.myDefaultValueString != null) return false;
    if (myExpressionString != null ? !myExpressionString.equals(variable.myExpressionString) : variable.myExpressionString != null) return false;
    if (!myName.equals(variable.myName)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result;
    result = myName.hashCode();
    result = 29 * result + (myAlwaysStopAt ? 1 : 0);
    result = 29 * result + (mySkipOnStart ? 1 : 0);
    result = 29 * result + (myExpressionString != null ? myExpressionString.hashCode() : 0);
    result = 29 * result + (myDefaultValueString != null ? myDefaultValueString.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "Variable{" +
           "myName='" + myName + '\'' +
           ", myAlwaysStopAt=" + myAlwaysStopAt +
           ", myExpressionString='" + myExpressionString + '\'' +
           ", myDefaultValueString='" + myDefaultValueString + '\'' +
           ", mySkipOnStart=" + mySkipOnStart +
           '}';
  }

  public boolean skipOnStart() {
    return mySkipOnStart;
  }
}
