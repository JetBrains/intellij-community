// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.sun.jdi.BooleanValue;

public abstract class ForStatementEvaluatorBase extends LoopEvaluator {
  public ForStatementEvaluatorBase(String labelName, Evaluator bodyEvaluator) {
    super(labelName, bodyEvaluator);
  }

  @Override
  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    Object value = context.getDebugProcess().getVirtualMachineProxy().mirrorOfVoid();
    value = evaluateInitialization(context, value);

    while (true) {
      // condition
      Object codition = evaluateCondition(context);
      if (codition instanceof Boolean) {
        if (!(Boolean)codition) break;
      }
      else if (codition instanceof BooleanValue) {
        if (!((BooleanValue)codition).booleanValue()) break;
      }
      else {
        throw EvaluateExceptionUtil.BOOLEAN_EXPECTED;
      }

      // body
      if (body(context)) break;

      // update
      value = evaluateUpdate(context, value);
    }

    return value;
  }

  protected Object evaluateInitialization(EvaluationContextImpl context, Object value) throws EvaluateException {
    return value;
  }

  protected Object evaluateCondition(EvaluationContextImpl context) throws EvaluateException {
    return true;
  }

  protected Object evaluateUpdate(EvaluationContextImpl context, Object value) throws EvaluateException {
    return value;
  }
}
