// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.sun.jdi.BooleanValue;
import org.jetbrains.annotations.NotNull;

public class DoWhileStatementEvaluator extends LoopEvaluator {
  private final Evaluator myConditionEvaluator;

  public DoWhileStatementEvaluator(@NotNull Evaluator conditionEvaluator, Evaluator bodyEvaluator, String labelName) {
    super(labelName, bodyEvaluator);
    myConditionEvaluator = DisableGC.create(conditionEvaluator);
  }

  @Override
  public Modifier getModifier() {
    return myConditionEvaluator.getModifier();
  }

  @Override
  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    Object value = context.getDebugProcess().getVirtualMachineProxy().mirrorOfVoid();
    while (true) {
      if (body(context)) break;

      value = myConditionEvaluator.evaluate(context);
      if (!(value instanceof BooleanValue)) {
        throw EvaluateExceptionUtil.BOOLEAN_EXPECTED;
      }
      else {
        if (!((BooleanValue)value).booleanValue()) {
          break;
        }
      }
    }

    return value;
  }
}
