// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.sun.jdi.ObjectReference;

public class CatchEvaluator implements Evaluator {
  private final String myExceptionType;
  private final String myParamName;
  private final CodeFragmentEvaluator myEvaluator;

  public CatchEvaluator(String exceptionType, String paramName, CodeFragmentEvaluator evaluator) {
    myExceptionType = exceptionType;
    myParamName = paramName;
    myEvaluator = evaluator;
  }

  public Object evaluate(ObjectReference exception, EvaluationContextImpl context) throws EvaluateException {
    myEvaluator.setValue(myParamName, exception);
    return myEvaluator.evaluate(context);
  }

  @Override
  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    throw new IllegalStateException("Use evaluate(ObjectReference exception, EvaluationContextImpl context)");
  }

  public String getExceptionType() {
    return myExceptionType;
  }
}
