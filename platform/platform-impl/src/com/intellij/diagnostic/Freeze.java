// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import java.util.List;

class Freeze extends Throwable {
  Freeze(List<StackTraceElement> stacktraceCommonPart) {
    setStackTrace(stacktraceCommonPart.toArray(new StackTraceElement[0]));
  }

  @Override
  public synchronized Throwable fillInStackTrace() {
    return this;
  }
}
