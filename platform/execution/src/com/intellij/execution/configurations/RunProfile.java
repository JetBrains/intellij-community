// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.configurations;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Base interface for things that can be executed (run configurations explicitly managed by user, or custom run profile implementations
 * created from code).
 *
 * @see RunConfiguration
 * @see ConfigurationFactory#createTemplateConfiguration(com.intellij.openapi.project.Project)
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/execution.html">Execution (IntelliJ Platform Docs)</a>
 */
public interface RunProfile {
   /**
    * Prepares for executing a specific instance of the run configuration.
    *
    * @param executor the execution mode selected by the user (run, debug, profile etc.)
    * @param environment the environment object containing additional settings for executing the configuration.
    * @return the {@link RunProfileState} describing the process which is about to be started, or {@code null}
    * if it's impossible to start the process.
    */
  @Nullable
  RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment environment) throws ExecutionException;

  /**
   * @return the name of the run configuration.
   */
  @NlsSafe
  @NotNull
  String getName();

  /**
   * Returns the icon for the run configuration. This icon is displayed in the tab showing the results of executing the run profile,
   * and for persistent run configuration is also used in the run configuration management UI.
   *
   * @return the icon for the run configuration, or null if the default executor icon should be used.
   */
  @Nullable
  Icon getIcon();
}