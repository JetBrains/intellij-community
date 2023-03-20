/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.execution;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.openapi.actionSystem.AnAction;
import org.jetbrains.annotations.NotNull;

/**
 * The result of executing a process.
 *
 * @see DefaultExecutionResult
 */
public interface ExecutionResult {
  /**
   * Returns the component displaying the result of executing the process. This could be a console, a test results view or another similar
   * component.
   *
   * @return the component displaying the result of executing the process
   */
  ExecutionConsole getExecutionConsole();

  /**
   * Returns the actions to display in the toolbar of the Run/Debug console tab.
   *
   * @return the array of toolbar actions to display.
   */
  AnAction @NotNull [] getActions();


  /**
   * Returns the ProcessHandler attached to the running process.
   *
   * @return the ProcessHandler instance.
   */
  ProcessHandler getProcessHandler();
}
