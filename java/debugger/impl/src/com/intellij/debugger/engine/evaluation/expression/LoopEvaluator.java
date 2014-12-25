/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.util.Comparing;

/**
 * @author egor
 */
public abstract class LoopEvaluator implements Evaluator {
  private final String myLabelName;
  private final Evaluator myBodyEvaluator;

  public LoopEvaluator(String labelName, Evaluator bodyEvaluator) {
    myLabelName = labelName;
    myBodyEvaluator = bodyEvaluator != null ? new DisableGC(bodyEvaluator) : null;
  }

  protected boolean body(EvaluationContextImpl context) throws EvaluateException {
    try {
      evaluateBody(context);
    }
    catch (BreakException e) {
      if (Comparing.equal(e.getLabelName(), myLabelName)) {
        return true;
      }
      else {
        throw e;
      }
    }
    catch (ContinueException e) {
      if (!Comparing.equal(e.getLabelName(), myLabelName)) {
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
