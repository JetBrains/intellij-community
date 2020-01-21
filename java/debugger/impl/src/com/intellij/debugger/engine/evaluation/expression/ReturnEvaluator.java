// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import org.jetbrains.annotations.Nullable;

public class ReturnEvaluator implements Evaluator {
  @Nullable private final Evaluator myReturnValueEvaluator;

  public ReturnEvaluator(@Nullable Evaluator returnValueEvaluator) {
    myReturnValueEvaluator = returnValueEvaluator;
  }

  @Override
  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    Object returnValue = myReturnValueEvaluator == null ?
                         context.getDebugProcess().getVirtualMachineProxy().mirrorOfVoid() :
                         myReturnValueEvaluator.evaluate(context);
    throw new ReturnException(returnValue);
  }

  public static class ReturnException extends EvaluateException {
    private final Object myReturnValue;

    public ReturnException(Object returnValue) {
      super("Return");
      myReturnValue = returnValue;
    }

    public Object getReturnValue() {
      return myReturnValue;
    }
  }
}
