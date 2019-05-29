// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author egor
 */
public class SwitchStatementEvaluator implements Evaluator {
  private final Evaluator myExpressionEvaluator;
  private final Evaluator[] myBodyEvaluators;
  private final String myLabelName;

  public SwitchStatementEvaluator(Evaluator expressionEvaluator, Evaluator[] bodyEvaluators, @Nullable String labelName) {
    myExpressionEvaluator = expressionEvaluator;
    myBodyEvaluators = bodyEvaluators;
    myLabelName = labelName;
  }

  @Override
  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    Object switchValue = UnBoxingEvaluator.unbox(myExpressionEvaluator.evaluate(context), context);
    try {
      boolean caseFound = false;
      for (Evaluator evaluator : myBodyEvaluators) {
        if (caseFound) {
          evaluator.evaluate(context);
        }
        else {
          Evaluator e = DisableGC.unwrap(evaluator);
          if (e instanceof SwitchCaseEvaluator) {
            SwitchCaseEvaluator caseEvaluator = (SwitchCaseEvaluator)e;
            if (caseEvaluator.myDefaultCase || caseEvaluator.match(switchValue, context)) {
              caseFound = true;
            }
          }
        }
      }
    }
    catch (BreakException e) {
      if (!Comparing.equal(e.getLabelName(), myLabelName)) {
        throw e;
      }
    }
    return null;
  }

  static class SwitchCaseEvaluator implements Evaluator {
    private final List<? extends Evaluator> myEvaluators;
    private final boolean myDefaultCase;

    SwitchCaseEvaluator(List<? extends Evaluator> evaluators, boolean defaultCase) {
      myEvaluators = evaluators;
      myDefaultCase = defaultCase;
    }

    boolean match(Object value, EvaluationContextImpl context) throws EvaluateException {
      for (Evaluator evaluator : myEvaluators) {
        if (value.equals(UnBoxingEvaluator.unbox(evaluator.evaluate(context), context))) {
          return true;
        }
      }
      return false;
    }


    @Override
    public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
      return null;
    }
  }
}
