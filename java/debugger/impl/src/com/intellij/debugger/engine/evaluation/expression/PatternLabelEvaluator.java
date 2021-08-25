// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.sun.jdi.BooleanValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PatternLabelEvaluator extends PatternMatchingBaseEvaluator {
  public PatternLabelEvaluator(@NotNull Evaluator selectorEvaluator,
                               @NotNull TypeEvaluator typeEvaluator,
                               @NotNull Evaluator patternVariable,
                               @Nullable Evaluator guardingEvaluator) {
    super(selectorEvaluator, typeEvaluator, patternVariable, guardingEvaluator);
  }

  @Override
  protected boolean evaluateGuardingExpression(EvaluationContextImpl context) throws EvaluateException {
    if (myGuardingEvaluator == null) return true;
    Object result = myGuardingEvaluator.evaluate(context);
    if (!(result instanceof BooleanValue)) {
      throw EvaluateExceptionUtil.BOOLEAN_EXPECTED;
    }
    return ((BooleanValue)result).booleanValue();
  }
}
