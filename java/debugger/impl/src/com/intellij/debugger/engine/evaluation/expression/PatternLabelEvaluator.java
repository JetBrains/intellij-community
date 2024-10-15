// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;

class PatternLabelEvaluator implements Evaluator {
  private static final Logger LOG = Logger.getInstance(PatternLabelEvaluator.class);

  protected final @NotNull Evaluator myOperandEvaluator;
  protected final @NotNull PatternEvaluator myPatternEvaluator;

  PatternLabelEvaluator(@NotNull Evaluator operandEvaluator,
                        @NotNull PatternEvaluator patternEvaluator) {
    myOperandEvaluator = operandEvaluator;
    myPatternEvaluator = patternEvaluator;
  }

  @Override
  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    Value value = (Value)myOperandEvaluator.evaluate(context);
    if (value == null) {
      return context.getVirtualMachineProxy().mirrorOf(false);
    }
    if (!(value instanceof ObjectReference)) {
      throw EvaluateExceptionUtil.createEvaluateException(JavaDebuggerBundle.message("evaluation.error.object.reference.expected"));
    }
    try {
      boolean res = myPatternEvaluator.match(value, context);
      return context.getVirtualMachineProxy().mirrorOf(res);
    }
    catch (Exception e) {
      LOG.debug(e);
      throw EvaluateExceptionUtil.createEvaluateException(e);
    }
  }

  @Override
  public String toString() {
    return myPatternEvaluator.myTypeEvaluator + " " + myPatternEvaluator;
  }
}
