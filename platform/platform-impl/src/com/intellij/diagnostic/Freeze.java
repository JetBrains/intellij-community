// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import java.util.List;

final class Freeze extends Throwable {
  Freeze(List<StackTraceElement> stacktraceCommonPart) {
    setStackTrace(stacktraceCommonPart.toArray(new StackTraceElement[0]));
  }

  @Override
  public synchronized Throwable fillInStackTrace() {
    return this;
  }
}
