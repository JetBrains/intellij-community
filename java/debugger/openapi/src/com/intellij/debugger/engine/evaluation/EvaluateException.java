// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.evaluation;

import com.intellij.openapi.diagnostic.Logger;
import com.sun.jdi.InvocationException;
import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.Nullable;

public class EvaluateException extends Exception {
  private static final Logger LOG = Logger.getInstance(EvaluateException.class);
  private ObjectReference myTargetException;

  public EvaluateException(final String message) {
    super(message);
    if (LOG.isDebugEnabled()) {
      LOG.debug(message);
    }
  }

  public EvaluateException(String msg, Throwable th) {
    super(msg, th);
    if (th instanceof EvaluateException evaluateException) {
      myTargetException = evaluateException.getExceptionFromTargetVM();
    }
    else if (th instanceof InvocationException invocationException) {
      myTargetException = invocationException.exception();
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug(msg);
    }
  }

  public @Nullable ObjectReference getExceptionFromTargetVM() {
    return myTargetException;
  }

  public void setTargetException(final ObjectReference targetException) {
    myTargetException = targetException;
  }

  @Override
  public String getMessage() {
    final String errorMessage = super.getMessage();
    if (errorMessage != null) {
      return errorMessage;
    }
    final Throwable cause = getCause();
    final String causeMessage = cause != null ? cause.getMessage() : null;
    if (causeMessage != null) {
      return causeMessage;
    }
    return "unknown error";
  }
}