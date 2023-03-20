// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.sun.jdi.Value;

/**
 * @author Eugene Zhuravlev
 */
public class IdentityEvaluator implements Evaluator {
  private final Value myValue;

  public IdentityEvaluator(Value value) {
    myValue = value;
  }

  @Override
  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    return myValue;
  }
}
