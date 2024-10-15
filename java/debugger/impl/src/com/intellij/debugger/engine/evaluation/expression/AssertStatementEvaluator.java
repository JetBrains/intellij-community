// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.util.ThreeState;
import com.sun.jdi.BooleanValue;
import com.sun.jdi.ClassObjectReference;
import com.sun.jdi.Method;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

public class AssertStatementEvaluator implements Evaluator {
  @NotNull private final Evaluator myEvaluator;

  public AssertStatementEvaluator(@NotNull Evaluator evaluator) {
    myEvaluator = evaluator;
  }

  @Override
  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    // first check if assertions are enabled
    StackFrameProxyImpl frameProxy = context.getFrameProxy();
    if (frameProxy == null) {
      throw EvaluateExceptionUtil.NULL_STACK_FRAME;
    }
    ThreeState status = DebuggerUtilsEx.getEffectiveAssertionStatus(frameProxy.location());
    if (status == ThreeState.UNSURE) {
      // No assertions compiled in current class, so assertions flag is not initialized.
      // Request the status from the Class#desiredAssertionStatus.
      ClassObjectReference classObjectReference = frameProxy.location().declaringType().classObject();
      Method method = DebuggerUtils.findMethod(classObjectReference.referenceType(), "desiredAssertionStatus", "()Z");
      if (method != null) {
        Value res = context.getDebugProcess().invokeMethod(context, classObjectReference, method, Collections.emptyList());
        if (res instanceof BooleanValue) {
          status = ThreeState.fromBoolean(((BooleanValue)res).value());
        }
      }
    }
    if (status == ThreeState.NO) {
      return context.getVirtualMachineProxy().mirrorOfVoid();
    }
    return myEvaluator.evaluate(context);
  }
}
