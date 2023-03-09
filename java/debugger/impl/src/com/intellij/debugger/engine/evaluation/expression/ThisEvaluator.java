// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * Class ThisEvaluator
 * @author Jeka
 */
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;

public class ThisEvaluator implements Evaluator {
  private final CaptureTraverser myTraverser;

  public ThisEvaluator() {
    this(CaptureTraverser.direct());
  }

  public ThisEvaluator(CaptureTraverser traverser) {
    myTraverser = traverser;
  }

  @Override
  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    Value objRef = context.computeThisObject(); // may be a primitive
    if (objRef instanceof ObjectReference) {
      objRef = myTraverser.traverse((ObjectReference)objRef);
    }
    if (objRef == null) {
      throw EvaluateExceptionUtil.createEvaluateException(JavaDebuggerBundle.message("evaluation.error.this.not.avalilable"));
    }
    return objRef;
  }

  @Override
  public String toString() {
    return "this";
  }
}
