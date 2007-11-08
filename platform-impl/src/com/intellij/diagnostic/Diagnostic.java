package com.intellij.diagnostic;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NonNls;

public class Diagnostic {
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

  public static boolean isAssertEnabled(String category) {
    return true;
  }

  public static void methodNotImplemented(String category) {
    methodNotImplemented(category, "");
  }

  public static void methodNotImplemented(String category, String message) {
    @NonNls String message1 = "METHOD NOT IMPLEMENTED YET " + message;
    Logger.getInstance(category).error(message1);
  }
}
