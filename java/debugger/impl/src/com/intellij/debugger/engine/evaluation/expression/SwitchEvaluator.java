// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.sun.jdi.StringReference;
import com.sun.jdi.Value;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public class SwitchEvaluator implements Evaluator {
  private final Evaluator myExpressionEvaluator;
  private final Evaluator[] myBodyEvaluators;
  private final String myLabelName;

  public SwitchEvaluator(Evaluator expressionEvaluator, Evaluator[] bodyEvaluators, @Nullable String labelName) {
    myExpressionEvaluator = expressionEvaluator;
    myBodyEvaluators = bodyEvaluators;
    myLabelName = labelName;
  }

  @Override
  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    Object switchValue = UnBoxingEvaluator.unbox(myExpressionEvaluator.evaluate(context), context);
    Object res = null;
    try {
      boolean caseFound = false;
      for (Evaluator evaluator : myBodyEvaluators) {
        if (caseFound) {
          res = evaluator.evaluate(context);
        }
        else {
          Evaluator e = DisableGC.unwrap(evaluator);
          if (e instanceof SwitchCaseEvaluator) {
            res = ((SwitchCaseEvaluator)e).match(switchValue, context);
            if (Boolean.TRUE.equals(res)) {
              caseFound = true;
            }
            else if (res instanceof Value) {
              return res;
            }
          }
        }
      }
    }
    catch (YieldException e) {
      return e.getValue();
    }
    catch (BreakException e) {
      if (!Objects.equals(e.getLabelName(), myLabelName)) {
        throw e;
      }
    }
    return res;
  }

  static class SwitchCaseEvaluator implements Evaluator {
    final List<? extends Evaluator> myEvaluators;
    final boolean myDefaultCase;

    SwitchCaseEvaluator(List<? extends Evaluator> evaluators, boolean defaultCase) {
      myEvaluators = evaluators;
      myDefaultCase = defaultCase;
    }

    Object match(Object value, EvaluationContextImpl context) throws EvaluateException {
      if (myDefaultCase) {
        return true;
      }
      for (Evaluator evaluator : myEvaluators) {
        if (resultsEquals(value, UnBoxingEvaluator.unbox(evaluator.evaluate(context), context))) {
          return true;
        }
      }
      return false;
    }

    static boolean resultsEquals(Object val1, Object val2) {
      if (val1 instanceof StringReference && val2 instanceof StringReference) {
        return ((StringReference)val1).value().equals(((StringReference)val2).value());
      }
      return val1.equals(val2);
    }

    @Override
    public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
      return null;
    }
  }

  static class SwitchCaseRuleEvaluator extends SwitchCaseEvaluator {
    final Evaluator myBodyEvaluator;

    SwitchCaseRuleEvaluator(List<? extends Evaluator> evaluators, boolean defaultCase, Evaluator bodyEvaluator) {
      super(evaluators, defaultCase);
      myBodyEvaluator = bodyEvaluator;
    }

    @Override
    Object match(Object value, EvaluationContextImpl context) throws EvaluateException {
      Object res = super.match(value, context);
      if (Boolean.TRUE.equals(res)) {
        return myBodyEvaluator.evaluate(context);
      }
      return res;
    }
  }

  static class YieldEvaluator implements Evaluator {
    @Nullable final Evaluator myValueEvaluator;

    YieldEvaluator(@Nullable Evaluator valueEvaluator) {
      myValueEvaluator = valueEvaluator;
    }

    @Override
    public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
      Object value = myValueEvaluator == null ?
                     context.getDebugProcess().getVirtualMachineProxy().mirrorOfVoid() :
                     myValueEvaluator.evaluate(context);
      throw new YieldException(value);
    }
  }

  static class YieldException extends EvaluateException {
    final Object myValue;

    YieldException(Object value) {
      super("Yield");
      myValue = value;
    }

    public Object getValue() {
      return myValue;
    }
  }
}
