// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;

class ArrayInitializerEvaluator implements Evaluator {
  private final Evaluator[] myValueEvaluators;

  ArrayInitializerEvaluator(Evaluator[] valueEvaluators) {
    myValueEvaluators = valueEvaluators;
  }

  /**
   * @return an array of Values
   */
  @Override
  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    Object[] values = new Object[myValueEvaluators.length];
    for (int idx = 0; idx < myValueEvaluators.length; idx++) {
      Evaluator evaluator = myValueEvaluators[idx];
      values[idx] = evaluator.evaluate(context);
    }
    return values;
  }
}
