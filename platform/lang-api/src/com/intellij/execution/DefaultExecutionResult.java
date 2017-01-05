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
import org.jetbrains.annotations.Nullable;

public class DefaultExecutionResult implements ExecutionResult {
  private final ExecutionConsole myConsole;
  private final ProcessHandler myProcessHandler;

  private AnAction[] myActions;
  private AnAction[] myRestartActions = AnAction.EMPTY_ARRAY;

  public DefaultExecutionResult() {
    myConsole = null;
    myProcessHandler = null;
    myActions = AnAction.EMPTY_ARRAY;
  }

  public DefaultExecutionResult(@Nullable ExecutionConsole console, @NotNull ProcessHandler processHandler) {
    this(console, processHandler, AnAction.EMPTY_ARRAY);
  }

  public DefaultExecutionResult(@NotNull ProcessHandler processHandler) {
    this(null, processHandler, AnAction.EMPTY_ARRAY);
  }

  public DefaultExecutionResult(ExecutionConsole console, @NotNull ProcessHandler processHandler, @NotNull AnAction... actions) {
    myConsole = console;
    myProcessHandler = processHandler;
    myActions = actions;
  }

  @Override
  public ExecutionConsole getExecutionConsole() {
    return myConsole;
  }

  @NotNull
  @Override
  public AnAction[] getActions() {
    return myActions;
  }

  public void setActions(@NotNull AnAction... actions) {
    myActions = actions;
  }

  @NotNull
  public AnAction[] getRestartActions() {
    return myRestartActions;
  }

  public void setRestartActions(@NotNull AnAction... restartActions) {
    myRestartActions = restartActions;
  }

  @NotNull
  @Deprecated
  public AnAction[] getAdditionalStopActions() {
    return AnAction.EMPTY_ARRAY;
  }

  @Override
  public ProcessHandler getProcessHandler() {
    return myProcessHandler;
  }
}
