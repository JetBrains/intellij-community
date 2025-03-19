// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;

@ApiStatus.Internal
public final class RecoveredThrowable extends Throwable {
  private final String myFullMessage;
  private final String myStacktrace;

  private RecoveredThrowable(String fullMessage, String stacktrace) {
    myFullMessage = fullMessage;
    myStacktrace = stacktrace;
  }

  @Override
  public String getMessage() {
    return myFullMessage;
  }

  @Override
  public void printStackTrace(PrintWriter s) {
    s.print(myStacktrace);
  }

  @Override
  public void printStackTrace(PrintStream s) {
    s.print(myStacktrace);
  }

  public static @NotNull Throwable fromString(@NotNull String stacktrace) {
    if (stacktrace.isBlank()) throw new IllegalArgumentException("stacktrace is empty");
    var lines = StringUtil.splitByLines(stacktrace);
    var result = new RecoveredThrowable(lines[0], stacktrace);
    var stack = new ArrayList<StackTraceElement>(lines.length - 1);
    for (int i = 1; i < lines.length; i++) {
      var line = lines[i].trim();
      if (!line.startsWith("at ")) break;
      stack.add(parseStackTraceLine(line.substring(3).trim()));
    }
    result.setStackTrace(stack.toArray(new StackTraceElement[0]));
    return result;
  }

  private static StackTraceElement parseStackTraceLine(String line) {
    var className = (String)null;
    var methodName = (String)null;
    var sourceFile = (String)null;
    int lineNumber = 0;

    var sourceStart = line.lastIndexOf('(');
    if (sourceStart > 0) {
      var sourceEnd = line.endsWith(")") ? line.length() - 1 : line.length();
      var source = line.substring(sourceStart + 1, sourceEnd);
      if (source.equalsIgnoreCase("Native Method")) {
        lineNumber = -2;  // see `java.lang.StackTraceElement#isNativeMethod`
      }
      else if (!source.equalsIgnoreCase("Unknown Source")) {
        var separator = source.lastIndexOf(':');
        if (separator < 0) {
          sourceFile = source;
        }
        else {
          sourceFile = source.substring(0, separator);
          try {
            lineNumber = Integer.parseInt(source.substring(separator + 1));
          }
          catch (NumberFormatException ignored) { }
        }
      }
      line = line.substring(0, sourceStart);
    }

    var nameStart = line.lastIndexOf('/');
    if (nameStart > 0) line = line.substring(nameStart + 1);
    var methodStart = line.lastIndexOf('.');
    if (methodStart < 0) {
      className = line;
      methodName = "<empty>";
    }
    else {
      className = line.substring(0, methodStart);
      methodName = line.substring(methodStart + 1);
    }

    StackTraceElement element = new StackTraceElement(className, methodName, sourceFile, lineNumber);
    return element;
  }
}
