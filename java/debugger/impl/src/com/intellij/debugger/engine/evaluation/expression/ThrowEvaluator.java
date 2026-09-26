// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.NotNull;

public class ThrowEvaluator implements Evaluator {
  private final @NotNull Evaluator myExceptionEvaluator;

  public ThrowEvaluator(@NotNull Evaluator exceptionEvaluator) {
    myExceptionEvaluator = exceptionEvaluator;
  }

  @Override
  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    ObjectReference exception = (ObjectReference)myExceptionEvaluator.evaluate(context);
    EvaluateException ex = new EvaluateException(
      JavaDebuggerBundle.message("evaluation.error.method.exception", exception.referenceType().name()));
    ex.setTargetException(exception);
    throw ex;
  }
}
