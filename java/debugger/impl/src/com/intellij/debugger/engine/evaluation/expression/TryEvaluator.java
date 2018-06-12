// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author egor
 */
public class TryEvaluator implements Evaluator {
  @NotNull private final Evaluator myBodyEvaluator;
  private final List<CatchEvaluator> myCatchBlockEvaluators;
  @Nullable private final Evaluator myFinallyEvaluator;

  public TryEvaluator(@NotNull Evaluator bodyEvaluator,
                      List<CatchEvaluator> catchBlockEvaluators,
                      @Nullable Evaluator finallyEvaluator) {
    myBodyEvaluator = bodyEvaluator;
    myCatchBlockEvaluators = catchBlockEvaluators;
    myFinallyEvaluator = finallyEvaluator;
  }

  @Override
  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    Object result = context.getDebugProcess().getVirtualMachineProxy().mirrorOfVoid();
    try {
      result = myBodyEvaluator.evaluate(context);
    } catch (EvaluateException e) {
      boolean catched = false;
      ObjectReference vmException = e.getExceptionFromTargetVM();
      if (vmException != null) {
        for (CatchEvaluator evaluator : myCatchBlockEvaluators) {
          if (evaluator != null && DebuggerUtils.instanceOf(vmException.type(), evaluator.getExceptionType())) {
            result = evaluator.evaluate(vmException, context);
            catched = true;
            break;
          }
        }
      }
      if (!catched) {
        throw e;
      }
    } finally {
      if (myFinallyEvaluator != null) {
        result = myFinallyEvaluator.evaluate(context);
      }
    }
    return result;
  }
}
