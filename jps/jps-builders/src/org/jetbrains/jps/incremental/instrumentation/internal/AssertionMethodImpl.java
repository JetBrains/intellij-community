/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.jps.incremental.instrumentation.internal;

import com.intellij.openapi.diagnostic.Logger;

/*
  A template for the synthetic method embedded by the bytecode instrumentation.
 */
class AssertionMethodImpl {
  private static void assertArgumentIsSystemIndependent(String className, String methodName, String parameterName, String argument) {
    if (argument != null && argument.indexOf('\\') > -1) {
      String message = String.format("Argument for @SystemIndependent parameter '%s' of %s.%s must be system-independent: %s",
                                     parameterName, className, methodName, argument);

      IllegalArgumentException exception = new IllegalArgumentException(message);

      StackTraceElement[] stackTrace = new StackTraceElement[exception.getStackTrace().length - 1];
      System.arraycopy(exception.getStackTrace(), 1, stackTrace, 0, stackTrace.length);
      exception.setStackTrace(stackTrace);

      Logger.getInstance("#org.jetbrains.jps.incremental.instrumentation.AssertionMethodImpl").error(exception);
    }
  }
}
