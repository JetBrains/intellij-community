// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * Class InstanceofEvaluator
 * @author Jeka
 */
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;

class InstanceofEvaluator implements Evaluator {
  private static final Logger LOG = Logger.getInstance(InstanceofEvaluator.class);
  private final Evaluator myOperandEvaluator;
  private final TypeEvaluator myTypeEvaluator;

  InstanceofEvaluator(Evaluator operandEvaluator, TypeEvaluator typeEvaluator) {
    myOperandEvaluator = operandEvaluator;
    myTypeEvaluator = typeEvaluator;
  }

  @Override
  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    Value value = (Value)myOperandEvaluator.evaluate(context);
    if (value == null) {
      return context.getDebugProcess().getVirtualMachineProxy().mirrorOf(false);
    }
    if (!(value instanceof ObjectReference)) {
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.object.reference.expected"));
    }
    try {
      return context.getDebugProcess().getVirtualMachineProxy().mirrorOf(
        DebuggerUtilsImpl.instanceOf(((ObjectReference)value).referenceType(), myTypeEvaluator.evaluate(context)));
    }
    catch (Exception e) {
      LOG.debug(e);
      throw EvaluateExceptionUtil.createEvaluateException(e);
    }
  }

  @Override
  public String toString() {
    return myOperandEvaluator + " instanceof " + myTypeEvaluator;
  }
}
