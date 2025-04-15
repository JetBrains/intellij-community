// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.SuspendContext;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.managerThread.SuspendContextCommand;
import com.intellij.xdebugger.impl.ui.tree.nodes.XEvaluationOrigin;
import com.sun.jdi.Value;

public abstract class ToStringCommand implements SuspendContextCommand {
  private final EvaluationContext myEvaluationContext;
  private final Value myValue;

  private boolean myIsEvaluated = false;

  protected ToStringCommand(EvaluationContext evaluationContext, Value value) {
    myEvaluationContext = evaluationContext;
    myValue = value;
  }

  @Override
  public void action() {
    if (myIsEvaluated) return;
    XEvaluationOrigin.computeWithOrigin(((EvaluationContextImpl)myEvaluationContext), XEvaluationOrigin.RENDERER, () -> {
      try {
        final String valueAsString = DebuggerUtils.getValueAsString(myEvaluationContext, myValue);
        evaluationResult(valueAsString);
      }
      catch (final EvaluateException ex) {
        evaluationError(ex.getMessage());
      }
      return null;
    });
  }

  @Override
  public void commandCancelled() {
  }

  public void setEvaluated() {
    myIsEvaluated = true;
  }

  @Override
  public SuspendContext getSuspendContext() {
    return myEvaluationContext.getSuspendContext();
  }

  public abstract void evaluationResult(String message);

  public abstract void evaluationError(String message);

  public Value getValue() {
    return myValue;
  }

  public EvaluationContext getEvaluationContext() {
    return myEvaluationContext;
  }
}

