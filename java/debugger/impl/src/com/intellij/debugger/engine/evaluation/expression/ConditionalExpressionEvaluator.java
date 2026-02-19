// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * Class ConditionalExpressionEvaluator
 * @author Jeka
 */
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.sun.jdi.BooleanValue;
import com.sun.jdi.Value;

class ConditionalExpressionEvaluator implements Evaluator {
  private final Evaluator myConditionEvaluator;
  private final Evaluator myThenEvaluator;
  private final Evaluator myElseEvaluator;

  ConditionalExpressionEvaluator(Evaluator conditionEvaluator, Evaluator thenEvaluator, Evaluator elseEvaluator) {
    myConditionEvaluator = conditionEvaluator;
    myThenEvaluator = thenEvaluator;
    myElseEvaluator = elseEvaluator;
  }

  @Override
  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    Value condition = (Value)myConditionEvaluator.evaluate(context);
    if (!(condition instanceof BooleanValue)) {
      throw EvaluateExceptionUtil.createEvaluateException(JavaDebuggerBundle.message("evaluation.error.boolean.condition.expected"));
    }
    return ((BooleanValue)condition).booleanValue() ? myThenEvaluator.evaluate(context) : myElseEvaluator.evaluate(context);
  }
}
