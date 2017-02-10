/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import org.jetbrains.annotations.Nullable;

/**
 * @author egor
 */
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
