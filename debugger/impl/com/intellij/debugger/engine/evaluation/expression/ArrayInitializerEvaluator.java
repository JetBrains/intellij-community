/**
 * class ArrayInitializerEvaluator
 * created Jun 28, 2001
 * @author Jeka
 */
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;

class ArrayInitializerEvaluator implements Evaluator{
  private final Evaluator[] myValueEvaluators;

  public ArrayInitializerEvaluator(Evaluator[] valueEvaluators) {
    myValueEvaluators = valueEvaluators;
  }

  /**
   * @return an array of Values
   */
  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    Object[] values = new Object[myValueEvaluators.length];
    for (int idx = 0; idx < myValueEvaluators.length; idx++) {
      Evaluator evaluator = myValueEvaluators[idx];
      values[idx] = evaluator.evaluate(context);
    }
    return values;
  }

  public Modifier getModifier() {
    return null;
  }
}
