// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * Interface Evaluator
 * @author Jeka
 */
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;

public interface Evaluator {
  Object evaluate(EvaluationContextImpl context) throws EvaluateException;

  /**
   * In order to obtain a modifier the expression must be evaluated first
   *
   * @return a modifier object allowing to set a value in case the expression is lvalue,
   * otherwise null is returned
   */
  default Modifier getModifier() {
    return null;
  }
}