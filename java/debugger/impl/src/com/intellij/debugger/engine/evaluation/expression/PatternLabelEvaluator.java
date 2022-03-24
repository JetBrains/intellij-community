// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.sun.jdi.BooleanValue;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class PatternLabelEvaluator implements Evaluator {
  private static final Logger LOG = Logger.getInstance(PatternLabelEvaluator.class);

  protected final Evaluator myOperandEvaluator;
  protected final TypeEvaluator myTypeEvaluator;
  private final Evaluator myPatternVariable;
  protected final Evaluator myGuardingEvaluator;

  PatternLabelEvaluator(@NotNull Evaluator operandEvaluator,
                        @NotNull TypeEvaluator typeEvaluator,
                        @Nullable Evaluator patternVariable,
                        @Nullable Evaluator guardingEvaluator) {
    myOperandEvaluator = operandEvaluator;
    myTypeEvaluator = typeEvaluator;
    myPatternVariable = patternVariable;
    myGuardingEvaluator = guardingEvaluator;
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
      res = res && evaluateGuardingExpression(context);
      return context.getDebugProcess().getVirtualMachineProxy().mirrorOf(res);
    }
    catch (Exception e) {
      LOG.debug(e);
      throw EvaluateExceptionUtil.createEvaluateException(e);
    }
  }

  protected boolean evaluateGuardingExpression(EvaluationContextImpl context) throws EvaluateException {
    if (myGuardingEvaluator == null) return true;
    Object result = myGuardingEvaluator.evaluate(context);
    if (!(result instanceof BooleanValue)) {
      throw EvaluateExceptionUtil.BOOLEAN_EXPECTED;
    }
    return ((BooleanValue)result).booleanValue();
  }
}
