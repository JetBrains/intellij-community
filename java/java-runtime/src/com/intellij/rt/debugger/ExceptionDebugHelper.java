// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.rt.debugger;

import java.io.PrintWriter;
import java.io.StringWriter;

public final class ExceptionDebugHelper {
  public static String getThrowableText(Throwable t) {
    StringWriter writer = new StringWriter();
    t.printStackTrace(new PrintWriter(writer));
    return writer.getBuffer().toString();
  }
}
