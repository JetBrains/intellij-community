// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.sun.jdi.Field;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.StringJoiner;

public class DeconstructionPatternEvaluator extends PatternEvaluator {
  private final @NotNull List<PatternEvaluator> myComponentEvaluators;
  private final @NotNull List<String> myRecordComponentNames;

  public DeconstructionPatternEvaluator(@NotNull TypeEvaluator typeEvaluator,
                                        @Nullable SyntheticVariableEvaluator variableEvaluator,
                                        @NotNull List<PatternEvaluator> componentEvaluators,
                                        @NotNull List<String> recordComponentNames) {
    super(typeEvaluator, variableEvaluator);
    myComponentEvaluators = componentEvaluators;
    myRecordComponentNames = recordComponentNames;
  }

  @Override
  boolean match(Value value, EvaluationContextImpl context) throws EvaluateException {
    if (value instanceof ObjectReference objRef && myComponentEvaluators.size() == myRecordComponentNames.size()) {
      assert myTypeEvaluator != null;
      String typeName = myTypeEvaluator.evaluate(context).name();
      if (typeName == null) return false;
      ReferenceType refType = objRef.referenceType();
      boolean res = DebuggerUtils.instanceOf(refType, typeName);
      for (int i = 0; i < myComponentEvaluators.size() && res; i++) {
        PatternEvaluator componentEvaluator = myComponentEvaluators.get(i);
        Field field = DebuggerUtils.findField(refType, myRecordComponentNames.get(i));
        res = componentEvaluator.match(objRef.getValue(field), context);
      }
      if (res && myVariableEvaluator != null) {
        AssignmentEvaluator.assign(myVariableEvaluator.getModifier(), value, context);
      }
      return res;
    }
    return false;
  }

  @Override
  public String toString() {
    StringJoiner joiner = new StringJoiner(", ");
    for (PatternEvaluator componentEvaluator : myComponentEvaluators) {
      joiner.add(componentEvaluator.toString());
    }
    return myTypeEvaluator + "(" + joiner + ") " + (myVariableEvaluator != null ? myVariableEvaluator : "");
  }
}

