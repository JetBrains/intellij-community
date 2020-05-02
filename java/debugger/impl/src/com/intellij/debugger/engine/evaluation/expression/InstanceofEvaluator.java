// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * Class InstanceofEvaluator
 * @author Jeka
 */
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;
import org.jetbrains.annotations.Nullable;

class InstanceofEvaluator implements Evaluator {
  private static final Logger LOG = Logger.getInstance(InstanceofEvaluator.class);
  private final Evaluator myOperandEvaluator;
  private final TypeEvaluator myTypeEvaluator;
  private final Evaluator myPatternVariable;

  InstanceofEvaluator(Evaluator operandEvaluator, TypeEvaluator typeEvaluator, @Nullable Evaluator patternVariable) {
    myOperandEvaluator = operandEvaluator;
    myTypeEvaluator = typeEvaluator;
    myPatternVariable = patternVariable;
  }

  @Override
  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    Value value = (Value)myOperandEvaluator.evaluate(context);
    if (value == null) {
      return context.getDebugProcess().getVirtualMachineProxy().mirrorOf(false);
    }
    if (!(value instanceof ObjectReference)) {
      throw EvaluateExceptionUtil.createEvaluateException(JavaDebuggerBundle.message("evaluation.error.object.reference.expected"));
    }
    try {
      boolean res = DebuggerUtilsImpl.instanceOf(((ObjectReference)value).referenceType(), myTypeEvaluator.evaluate(context));
      if (res && myPatternVariable != null) {
        AssignmentEvaluator.assign(myPatternVariable.getModifier(), value, context);
      }
      return context.getDebugProcess().getVirtualMachineProxy().mirrorOf(res);
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
