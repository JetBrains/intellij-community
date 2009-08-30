/*
 * Interface Evaluator
 * @author Jeka
 */
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;

public interface Evaluator {
  /**
   * @throws com.intellij.debugger.engine.evaluation.EvaluateException
   */
  Object evaluate(EvaluationContextImpl context) throws EvaluateException;

  /**
   * In order to obtain a modifier the expression must be evaluated first
   * @return a modifier object allowing to set a value in case the expression is lvalue,
   *         otherwise null is returned
   */
  Modifier getModifier();
}