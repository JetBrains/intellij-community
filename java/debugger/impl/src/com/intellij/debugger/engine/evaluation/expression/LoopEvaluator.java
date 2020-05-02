// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;

import java.util.Objects;

public abstract class LoopEvaluator implements Evaluator {
  private final String myLabelName;
  private final Evaluator myBodyEvaluator;

  public LoopEvaluator(String labelName, Evaluator bodyEvaluator) {
    myLabelName = labelName;
    myBodyEvaluator = bodyEvaluator != null ? DisableGC.create(bodyEvaluator) : null;
  }

  protected boolean body(EvaluationContextImpl context) throws EvaluateException {
    try {
      evaluateBody(context);
    }
    catch (BreakException e) {
      if (Objects.equals(e.getLabelName(), myLabelName)) {
        return true;
      }
      else {
        throw e;
      }
    }
    catch (ContinueException e) {
      if (!Objects.equals(e.getLabelName(), myLabelName)) {
        throw e;
      }
    }
    return false;
  }

  public String getLabelName() {
    return myLabelName;
  }

  protected void evaluateBody(EvaluationContextImpl context) throws EvaluateException {
    if (myBodyEvaluator != null) {
      myBodyEvaluator.evaluate(context);
    }
  }
}
