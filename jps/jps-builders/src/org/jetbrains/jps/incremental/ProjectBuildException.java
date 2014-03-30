/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.incremental;

/**
 * Use this exception to signal that the build must be stopped
 *
 * If message is provided, the build is stopped and specified message is shown as an error message and exception is logged to log.
 * If only Throwable cause of the stop is provided, the reason is assumed to be an unexpected internal error,
 * so the corresponding error message "internal error" with stacktrace is reported
 */
public class ProjectBuildException extends Exception{

  /**
   * Causes the build to be stopped and error message shown 
   * @param message a message to be shown as an error 
   */
  public ProjectBuildException(String message) {
    super(message);
  }

  /**
   * Causes the build to be stopped and error message shown 
   * @param message a message to be shown as an error
   * @param cause additional information that caused error; its stacktrace will be only logged and not shown in UI 
   */
  public ProjectBuildException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Such exception is treated as an unexpected internal error, so the trace of the 'cause' will be shown in UI
   * @param cause
   */
  public ProjectBuildException(Throwable cause) {
    super(cause);
  }
}
