// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.List;

class Freeze extends Throwable {
  private final String myStackText;

  Freeze(List<StackTraceElement> stacktraceCommonPart) {
    myStackText = null;
    setStackTrace(stacktraceCommonPart.toArray(new StackTraceElement[0]));
  }

  Freeze(String stackText) {
    myStackText = stackText;
  }

  @Override
  public void printStackTrace(PrintStream s) {
    if (myStackText != null) {
      s.print(myStackText);
    }
    else {
      super.printStackTrace(s);
    }
  }

  @Override
  public void printStackTrace(PrintWriter s) {
    if (myStackText != null) {
      s.print(myStackText);
    }
    else {
      super.printStackTrace(s);
    }
  }

  @Override
  public synchronized Throwable fillInStackTrace() {
    return this;
  }
}
