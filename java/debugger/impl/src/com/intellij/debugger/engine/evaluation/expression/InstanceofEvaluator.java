// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * Class InstanceofEvaluator
 * @author Jeka
 */
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class InstanceofEvaluator extends PatternLabelEvaluator {
  InstanceofEvaluator(@NotNull Evaluator operandEvaluator,
                      @NotNull TypeEvaluator typeEvaluator,
                      @Nullable Evaluator patternVariable) {
    super(operandEvaluator, typeEvaluator, patternVariable, null);
  }

  @Override
  protected boolean evaluateGuardingExpression(EvaluationContextImpl context) {
    return true;
  }

  @Override
  public String toString() {
    return myOperandEvaluator + " instanceof " + myTypeEvaluator;
  }
}
