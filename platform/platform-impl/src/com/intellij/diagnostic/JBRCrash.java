// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

public final class JBRCrash extends Throwable {
  @Override
  public synchronized Throwable fillInStackTrace() {
    return this;
  }
}
