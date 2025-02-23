// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.sun.jdi.BooleanValue;

public class IfStatementEvaluator implements Evaluator {
  private final Evaluator myConditionEvaluator;
  private final Evaluator myThenEvaluator;
  private final Evaluator myElseEvaluator;

  public IfStatementEvaluator(Evaluator conditionEvaluator, Evaluator thenEvaluator, Evaluator elseEvaluator) {
    myConditionEvaluator = DisableGC.create(conditionEvaluator);
    myThenEvaluator = DisableGC.create(thenEvaluator);
    myElseEvaluator = elseEvaluator == null ? null : DisableGC.create(elseEvaluator);
  }

  @Override
  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    Object value = myConditionEvaluator.evaluate(context);
    if (!(value instanceof BooleanValue)) {
      throw EvaluateExceptionUtil.BOOLEAN_EXPECTED;
    }
    else {
      if (((BooleanValue)value).booleanValue()) {
        value = myThenEvaluator.evaluate(context);
      }
      else {
        if (myElseEvaluator != null) {
          value = myElseEvaluator.evaluate(context);
        }
        else {
          value = context.getVirtualMachineProxy().mirrorOfVoid();
        }
      }
    }
    return value;
  }
}
