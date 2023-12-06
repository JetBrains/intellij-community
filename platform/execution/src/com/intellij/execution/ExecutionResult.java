// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.openapi.actionSystem.AnAction;
import org.jetbrains.annotations.NotNull;

/**
 * The result of executing a process.
 *
 * @see DefaultExecutionResult
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/execution.html">Execution (IntelliJ Platform Docs)</a>
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
   * @return the actions to display in the toolbar of the Run/Debug console tab
   */
  AnAction @NotNull [] getActions();

  /**
   * @return ProcessHandler attached to the running process
   */
  ProcessHandler getProcessHandler();
}
