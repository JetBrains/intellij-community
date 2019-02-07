// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions;

import com.intellij.openapi.diagnostic.ControlFlowException;

public final class ExtensionNotApplicableException extends RuntimeException implements ControlFlowException {
  public static final ExtensionNotApplicableException INSTANCE = new ExtensionNotApplicableException();

  private ExtensionNotApplicableException() {
  }

  @Override
  public synchronized Throwable fillInStackTrace() {
    return this;
  }
}
