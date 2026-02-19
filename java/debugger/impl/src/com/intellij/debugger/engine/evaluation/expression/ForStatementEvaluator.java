// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;

public class ForStatementEvaluator extends ForStatementEvaluatorBase {
  private final Evaluator myInitializationEvaluator;
  private final Evaluator myConditionEvaluator;
  private final Evaluator myUpdateEvaluator;

  public ForStatementEvaluator(Evaluator initializationEvaluator,
                               Evaluator conditionEvaluator,
                               Evaluator updateEvaluator,
                               Evaluator bodyEvaluator,
                               String labelName) {
    super(labelName, bodyEvaluator);
    myInitializationEvaluator = initializationEvaluator != null ? DisableGC.create(initializationEvaluator) : null;
    myConditionEvaluator = conditionEvaluator != null ? DisableGC.create(conditionEvaluator) : null;
    myUpdateEvaluator = updateEvaluator != null ? DisableGC.create(updateEvaluator) : null;
  }

  @Override
  protected Object evaluateInitialization(EvaluationContextImpl context, Object value) throws EvaluateException {
    if (myInitializationEvaluator != null) {
      return myInitializationEvaluator.evaluate(context);
    }
    return value;
  }

  @Override
  protected Object evaluateCondition(EvaluationContextImpl context) throws EvaluateException {
    if (myConditionEvaluator != null) {
      return myConditionEvaluator.evaluate(context);
    }
    return true;
  }

  @Override
  protected Object evaluateUpdate(EvaluationContextImpl context, Object value) throws EvaluateException {
    if (myUpdateEvaluator != null) {
      return myUpdateEvaluator.evaluate(context);
    }
    return value;
  }
}
