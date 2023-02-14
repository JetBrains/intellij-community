// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PatternEvaluator implements Evaluator {
  private final @Nullable String myPrimitiveType;
  protected final @Nullable TypeEvaluator myTypeEvaluator;
  protected final @Nullable SyntheticVariableEvaluator myVariableEvaluator;

  public PatternEvaluator(@NotNull TypeEvaluator typeEvaluator,
                          @Nullable SyntheticVariableEvaluator variableEvaluator) {
    myPrimitiveType = null;
    myTypeEvaluator = typeEvaluator;
    myVariableEvaluator = variableEvaluator;
  }

  public PatternEvaluator(@NotNull String primitiveType,
                          @Nullable SyntheticVariableEvaluator variableEvaluator) {
    myPrimitiveType = primitiveType;
    myTypeEvaluator = null;
    myVariableEvaluator = variableEvaluator;
  }

  boolean match(Value value, EvaluationContextImpl context) throws EvaluateException {
    String typeName = myTypeEvaluator != null ? myTypeEvaluator.evaluate(context).name() : myPrimitiveType;
    if (typeName == null) return false;
    boolean res = DebuggerUtils.instanceOf(value.type(), typeName);
    if (res && myVariableEvaluator != null) {
      AssignmentEvaluator.assign(myVariableEvaluator.getModifier(), value, context);
    }
    return res;
  }

  @Override
  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    return null;
  }

  @Override
  public String toString() {
    if (myPrimitiveType != null) {
      return myPrimitiveType + " " + myVariableEvaluator;
    }
    return myTypeEvaluator + " " + (myVariableEvaluator == null ? "" : myVariableEvaluator);
  }
}
