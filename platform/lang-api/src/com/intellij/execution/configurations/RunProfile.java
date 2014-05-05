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

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.runners.ExecutionEnvironment;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Base interface for things that can be executed (run configurations explicitly managed by user, or custom run profile implementations
 * created from code).
 *
 * @see RunConfiguration
 * @see ConfigurationFactory#createTemplateConfiguration(com.intellij.openapi.project.Project)
 */
public interface RunProfile {
   /**
    * Prepares for executing a specific instance of the run configuration.
    *
    * @param executor the execution mode selected by the user (run, debug, profile etc.)
    * @param environment the environment object containing additional settings for executing the configuration.
    * @return the RunProfileState describing the process which is about to be started, or null if it's impossible to start the process.
    */
  @Nullable
  RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment environment) throws ExecutionException;

  /**
   * Returns the name of the run configuration.
   *
   * @return the name of the run configuration.
   */
  String getName();

  /**
   * Returns the icon for the run configuration. This icon is displayed in the tab showing the results of executing the run profile,
   * and for persistent run configurations is also used in the run configuration management UI.
   *
   * @return the icon for the run configuration, or null if the default executor icon should be used.
   */
  @Nullable
  Icon getIcon();
}