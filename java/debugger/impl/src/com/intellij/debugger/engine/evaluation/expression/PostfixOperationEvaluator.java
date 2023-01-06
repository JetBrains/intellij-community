// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;

public class PostfixOperationEvaluator implements Evaluator {
  private final Evaluator myOperandEvaluator;

  private final Evaluator myIncrementImpl;

  private Modifier myModifier;

  public PostfixOperationEvaluator(Evaluator operandEvaluator, Evaluator incrementImpl) {
    myOperandEvaluator = DisableGC.create(operandEvaluator);
    myIncrementImpl = DisableGC.create(incrementImpl);
  }

  @Override
  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    final Object value = myOperandEvaluator.evaluate(context);
    myModifier = myOperandEvaluator.getModifier();
    Object operationResult = myIncrementImpl.evaluate(context);
    AssignmentEvaluator.assign(myModifier, operationResult, context);
    return value;
  }

  @Override
  public Modifier getModifier() {
    return myModifier;
  }
}
