// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import org.jetbrains.annotations.NotNull;

public class PostfixOperationEvaluator implements ModifiableEvaluator {
  private final Evaluator myOperandEvaluator;

  private final Evaluator myIncrementImpl;

  private Modifier myModifier;

  public PostfixOperationEvaluator(Evaluator operandEvaluator, Evaluator incrementImpl) {
    myOperandEvaluator = DisableGC.create(operandEvaluator);
    myIncrementImpl = DisableGC.create(incrementImpl);
  }

  @Override
  public @NotNull ModifiableValue evaluateModifiable(@NotNull EvaluationContextImpl context) throws EvaluateException {
    ModifiableValue modifiableValue = myOperandEvaluator.evaluateModifiable(context);
    myModifier = modifiableValue.getModifier();
    Object operationResult = myIncrementImpl.evaluate(context);
    AssignmentEvaluator.assign(myModifier, operationResult, context);
    return modifiableValue;
  }

  @Override
  public Modifier getModifier() {
    return myModifier;
  }
}
