/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

  public static boolean isJavaAssertionsEnabled() {
    boolean result = false;
    assert result = true;
    return result;
  }

  public static void methodNotImplemented(String category) {
    methodNotImplemented(category, "");
  }

  public static void methodNotImplemented(String category, String message) {
    @NonNls String message1 = "METHOD NOT IMPLEMENTED YET " + message;
    Logger.getInstance(category).error(message1);
  }
}
