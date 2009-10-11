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
package com.intellij.execution.configurations;

import org.jetbrains.annotations.NonNls;

public interface DebuggingRunnerData {
  @NonNls String DEBUGGER_RUNNER_ID = "Debug";

  /**
   * @return a string denoting debug port. In case of socket transport this is a number, in case of shared memory transport this is a string
   */
  String getDebugPort();

  void setDebugPort(String port);

  boolean isRemote();

  void setLocal(boolean isLocal);
}
