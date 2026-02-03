// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsImpl;
import com.sun.jdi.PrimitiveType;
import com.sun.jdi.ReferenceType;
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
    boolean res = false;
    if (myTypeEvaluator != null) {
      if (value.type() instanceof ReferenceType referenceType) {
        res = DebuggerUtilsImpl.instanceOf(referenceType, myTypeEvaluator.evaluate(context));
      }
    }
    else if (value.type() instanceof PrimitiveType primitiveType) {
      res = primitiveType.name().equals(myPrimitiveType);
    }
    if (res && myVariableEvaluator != null) {
      AssignmentEvaluator.assign(myVariableEvaluator.evaluateModifiable(context).getModifier(), value, context);
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
