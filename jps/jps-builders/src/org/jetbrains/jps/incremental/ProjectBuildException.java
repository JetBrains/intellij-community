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
 * If Throwable cause of the stop is provided, the reason is assumed to be an unexpected internal error,
 * so the corresponding error message "internal error" with stacktrace is additionally reported
 *
 * If no Throwable cause is provided, it is assumed that all the errors were reported by the builder previously and the build is just stopped
 * Optional message, if provided, is reported as a progress message.
 */
public class ProjectBuildException extends Exception{
  public ProjectBuildException() {
  }

  public ProjectBuildException(String message) {
    super(message);
  }

  public ProjectBuildException(String message, Throwable cause) {
    super(message, cause);
  }

  public ProjectBuildException(Throwable cause) {
    super(cause);
  }

  @Override
  public Throwable fillInStackTrace() {
    return this;
  }
}
