// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.sun.jdi.IncompatibleThreadStateException;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class RetryEvaluationException extends EvaluateException {
  public RetryEvaluationException(String message, IncompatibleThreadStateException e) {
    super(message, e);
  }
}
