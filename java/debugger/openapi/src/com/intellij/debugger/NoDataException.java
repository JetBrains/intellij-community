// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger;

import com.intellij.openapi.diagnostic.ControlFlowException;

public class NoDataException extends Exception implements ControlFlowException {
  public static final NoDataException INSTANCE = new NoDataException();

  /** @deprecated Use shared {@link NoDataException#INSTANCE} instead */
  @Deprecated(forRemoval = true)
  public NoDataException() { }

  @Override
  public synchronized Throwable fillInStackTrace() {
    return this;
  }
}
