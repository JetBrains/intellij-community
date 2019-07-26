// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

/**
 * @author egor
 */
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
    ClassObjectReference classObjectReference = frameProxy.location().declaringType().classObject();
    Method method = DebuggerUtils.findMethod(classObjectReference.referenceType(), "desiredAssertionStatus", "()Z");
    if (method != null) {
      Value res = context.getDebugProcess().invokeMethod(context, classObjectReference, method, Collections.emptyList());
      if (res instanceof BooleanValue && !((BooleanValue)res).value()) {
        return context.getDebugProcess().getVirtualMachineProxy().mirrorOfVoid();
      }
    }
    return myEvaluator.evaluate(context);
  }
}
