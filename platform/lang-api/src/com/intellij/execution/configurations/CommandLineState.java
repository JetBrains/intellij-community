// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.configurations;

import com.intellij.execution.*;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base implementation of {@link RunProfileState}. Takes care of putting together a process and a console and wrapping them into an
 * {@link ExecutionResult}. Does not contain any logic for actually starting the process.
 *
 * @see com.intellij.execution.configurations.JavaCommandLineState
 * @see GeneralCommandLine
 */
public abstract class CommandLineState implements RunProfileState {
  private TextConsoleBuilder myConsoleBuilder;

  private final ExecutionEnvironment myEnvironment;

  protected CommandLineState(ExecutionEnvironment environment) {
    myEnvironment = environment;
    if (myEnvironment != null) {
      final Project project = myEnvironment.getProject();
      final GlobalSearchScope searchScope = SearchScopeProvider.createSearchScope(project, myEnvironment.getRunProfile());
      myConsoleBuilder = TextConsoleBuilderFactory.getInstance().createBuilder(project, searchScope);
    }
  }

  public ExecutionEnvironment getEnvironment() {
    return myEnvironment;
  }

  @Nullable
  public RunnerSettings getRunnerSettings() {
    return myEnvironment.getRunnerSettings();
  }

  @NotNull
  public ExecutionTarget getExecutionTarget() {
    return myEnvironment.getExecutionTarget();
  }

  public void addConsoleFilters(Filter... filters) {
    myConsoleBuilder.filters(filters);
  }

  @Override
  @NotNull
  public ExecutionResult execute(@NotNull final Executor executor, @NotNull final ProgramRunner runner) throws ExecutionException {
    final ProcessHandler processHandler = startProcess();
    final ConsoleView console = createConsole(executor);
    if (console != null) {
      console.attachToProcess(processHandler);
    }
    return new DefaultExecutionResult(console, processHandler, createActions(console, processHandler, executor));
  }

  @Nullable
  protected ConsoleView createConsole(@NotNull final Executor executor) throws ExecutionException {
    TextConsoleBuilder builder = getConsoleBuilder();
    return builder != null ? builder.getConsole() : null;
  }

  /**
   * Starts the process.
   *
   * @return the handler for the running process
   * @throws ExecutionException if the execution failed.
   * @see GeneralCommandLine
   * @see com.intellij.execution.process.OSProcessHandler
   */
  @NotNull
  protected abstract ProcessHandler startProcess() throws ExecutionException;

  @NotNull
  protected AnAction[] createActions(final ConsoleView console, final ProcessHandler processHandler) {
    return createActions(console, processHandler, null);
  }

  @NotNull
  protected AnAction[] createActions(final ConsoleView console, final ProcessHandler processHandler, Executor executor) {
    return AnAction.EMPTY_ARRAY;
  }

  public TextConsoleBuilder getConsoleBuilder() {
    return myConsoleBuilder;
  }

  public void setConsoleBuilder(final TextConsoleBuilder consoleBuilder) {
    myConsoleBuilder = consoleBuilder;
  }
}
