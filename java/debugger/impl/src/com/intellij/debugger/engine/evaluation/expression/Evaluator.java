// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * Interface Evaluator
 * @author Jeka
 */
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;

public interface Evaluator {
  default Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    throw new AbstractMethodError("evaluate or evaluateModifiable must be implemented");
  }

  /**
   * Implement if the value may be modified (like local variable or a field)
   */
  default ModifiableValue evaluateModifiable(EvaluationContextImpl context) throws EvaluateException {
    return new ModifiableValue(evaluate(context), getModifier());
  }

  /**
   * In order to obtain a modifier the expression must be evaluated first
   *
   * @return a modifier object allowing to set a value in case the expression is lvalue,
   * otherwise null is returned
   *
   * @deprecated implement {@link #evaluateModifiable(EvaluationContextImpl)} instead
   * @see ModifiableEvaluator
   */
  @Deprecated
  default Modifier getModifier() {
    return null;
  }
}