// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class JBRCrash extends Throwable {
  @Override
  public synchronized Throwable fillInStackTrace() {
    return this;
  }
}
