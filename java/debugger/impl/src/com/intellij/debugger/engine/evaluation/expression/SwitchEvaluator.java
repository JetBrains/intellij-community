// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.sun.jdi.BooleanValue;
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
    Object switchValue = myExpressionEvaluator.evaluate(context);
    Object unboxedSwitchValue = switchValue != null ? UnBoxingEvaluator.unbox(switchValue, context) : null;
    Object res = null;
    try {
      boolean caseFound = false;
      int defaultLabelNum = -1;
      for (int i = 0; i < myBodyEvaluators.length; i++) {
        Evaluator evaluator = myBodyEvaluators[i];
        if (caseFound) {
          res = evaluator.evaluate(context);
        }
        else {
          Evaluator e = DisableGC.unwrap(evaluator);
          if (e instanceof SwitchCaseEvaluator caseEvaluator) {
            if (caseEvaluator.myDefaultCase) {
              defaultLabelNum = i;
              continue;
            }
            res = caseEvaluator.match(unboxedSwitchValue, context);
            if (Boolean.TRUE.equals(res)) {
              caseFound = true;
            }
            else if (res instanceof Value) {
              return res;
            }
          }
        }
      }
      if (!caseFound && defaultLabelNum != -1) {
        for (int i = defaultLabelNum; i < myBodyEvaluators.length; i++) {
          Evaluator evaluator = myBodyEvaluators[i];
          if (caseFound) {
            res = evaluator.evaluate(context);
          }
          else {
            caseFound = true;
            Evaluator e = DisableGC.unwrap(evaluator);
            if (e instanceof SwitchCaseEvaluator) {
              res = ((SwitchCaseEvaluator)e).match(unboxedSwitchValue, context);
              if (res instanceof Value) {
                return res;
              }
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
    final @Nullable Evaluator myGuardEvaluator;
    final boolean myDefaultCase;

    SwitchCaseEvaluator(List<? extends Evaluator> evaluators, @Nullable Evaluator guardEvaluator, boolean defaultCase) {
      myEvaluators = evaluators;
      myGuardEvaluator = guardEvaluator;
      myDefaultCase = defaultCase;
    }

    Object match(Object value, EvaluationContextImpl context) throws EvaluateException {
      // According to JEP 406, to maintain backward compatibility with the old
      // semantics of switch, the default label does not match a null selector.
      if (myDefaultCase && value != null) {
        return true;
      }
      for (Evaluator evaluator : myEvaluators) {
        if (evaluator instanceof PatternLabelEvaluator) {
          if (((BooleanValue)evaluator.evaluate(context)).booleanValue() &&
              (myGuardEvaluator == null || myGuardEvaluator.evaluate(context) instanceof BooleanValue bool && bool.booleanValue())) {
            return true;
          }
          continue;
        }
        Object labelValue = evaluator.evaluate(context);
        Object unboxedLabelValue = labelValue != null ? UnBoxingEvaluator.unbox(labelValue, context) : null;
        if (resultsEquals(value, unboxedLabelValue)) {
          return true;
        }
      }
      return false;
    }

    static boolean resultsEquals(Object val1, Object val2) {
      if (val1 == null || val2 == null) {
        return val1 == val2;
      }
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

    SwitchCaseRuleEvaluator(List<? extends Evaluator> evaluators, @Nullable Evaluator guardEvaluator, boolean defaultCase, Evaluator bodyEvaluator) {
      super(evaluators, guardEvaluator, defaultCase);
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
                     context.getVirtualMachineProxy().mirrorOfVoid() :
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
