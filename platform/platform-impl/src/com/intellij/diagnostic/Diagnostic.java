// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.openapi.diagnostic.Logger;

public final class Diagnostic {
  public static void trace(String category, String message) {
    Logger.getInstance(category).debug(message);
  }

  public static boolean isTraceEnabled(String category) {
    return Logger.getInstance(category).isDebugEnabled();
  }

  public static void trace(String category, Throwable exception) {
    Logger.getInstance(category).error(exception);
  }

  public static boolean assertTrue(String category, String message, boolean condition) {
    if (condition) return true;
    return Logger.getInstance(category).assertTrue(condition, message);
  }
}
