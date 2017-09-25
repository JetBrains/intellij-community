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
package com.intellij.execution;

import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.util.Factory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Describes a complete persisted run configuration (as displayed in the Run/Debug Configurations dialog), together with runner-specific
 * settings.
 *
 * @author anna
 * @see RunManager#createRunConfiguration(String, com.intellij.execution.configurations.ConfigurationFactory)
 */
public interface RunnerAndConfigurationSettings {
  /**
   * Returns the type of the run configuration.
   */
  @NotNull
  ConfigurationType getType();

  /**
   * Returns the factory used to create the run configuration.
   *
   * @return the factory
   */
  @NotNull
  ConfigurationFactory getFactory();

  /**
   * Returns true if this configuration settings object represents a template used to create other configurations of the same type
   * (in other words, an item under the "Defaults" node of the Run/Debug Configurations dialog).
   *
   * @return true if the configuration is a template, false otherwise.
   */
  boolean isTemplate();

  /**
   * Returns true if this configuration is temporary and will be deleted when the temporary configurations limit is exceeded.
   *
   * @return true if the configuration is temporary, false otherwise.
   */
  boolean isTemporary();

  /**
   * Is stored in the versioned part of the project files
   */
  default boolean isShared() {
    return false;
  }

  default void setShared(boolean value) {
  }

  /**
   * Marks the configuration as temporary or permanent.
   *
   * @param temporary true if the configuration is temporary, false if it's permanent.
   */
  void setTemporary(boolean temporary);

  /**
   * Returns the {@link RunConfiguration} instance that will be used to execute this run configuration.
   */
  @NotNull
  RunConfiguration getConfiguration();

  /**
   * Sets the name of the configuration.
   *
   * @param name the name of the configuration
   */
  void setName(String name);

  /**
   * Returns the name of the configuration.
   *
   * @return the name of the configuration.
   */
  @NotNull
  String getName();

  String getUniqueID();

  /**
   * Returns the runner-managed settings for the specified runner.
   *
   * @param runner the runner for which the settings are requested.
   * @return the settings, or null if the runner doesn't provide any settings or the settings aren't configured for this configuration.
   */
  @Nullable
  RunnerSettings getRunnerSettings(@NotNull ProgramRunner runner);

  /**
   * Returns the configuration-managed settings for the specified runner.
   *
   * @param runner the runner for which the settings are requested.
   * @return the settings, or null if the configuration doesn't provide any settings specific to this runner or the settings aren't
   * configured for this configuration.
   */
  @Nullable
  ConfigurationPerRunnerSettings getConfigurationSettings(@NotNull ProgramRunner runner);

  /**
   * Checks whether the run configuration settings are valid.
   *
   * @throws RuntimeConfigurationException if the configuration settings contain a non-fatal problem which the user should be warned about
   * but the execution should still be allowed
   * @throws RuntimeConfigurationError if the configuration settings contain a fatal problem which makes it impossible to execute the run
   * configuration.
   */
  default void checkSettings() throws RuntimeConfigurationException {
    checkSettings(null);
  }

  /**
   * Checks whether the run configuration settings are valid for execution with the specified executor.
   *
   * @param executor the executor which will be used to run the configuration, or null if the check is not specific to an executor.
   * @throws RuntimeConfigurationException if the configuration settings contain a non-fatal problem which the user should be warned about
   * but the execution should still be allowed
   * @throws RuntimeConfigurationError if the configuration settings contain a fatal problem which makes it impossible to execute the run
   * configuration.
   */
  void checkSettings(@Nullable Executor executor) throws RuntimeConfigurationException;

  /**
   * Checks if this configuration supports running on the provided target (see {@link ExecutionTarget} for details).
   * @param target target provided by {@link ExecutionTargetProvider}
   */
  boolean canRunOn(@NotNull ExecutionTarget target);

  /**
   * Returns a factory object which can be used to create a copy of this configuration.
   *
   * @return copying factory instance
   */
  Factory<RunnerAndConfigurationSettings> createFactory();

  /**
   * Sets the "Before launch: Show this page" flag (for showing the run configuration settings before execution).
   *
   * @param b if true, the settings dialog will be displayed before launching this configuration.
   */
  void setEditBeforeRun(boolean b);

  /**
   * Returns the "Before launch: Show this page" flag (for showing the run configuration settings before execution).
   *
   * @return if true, the settings dialog will be displayed before launching this configuration.
   */
  boolean isEditBeforeRun();

  /**
   * Sets the "Before launch: Activate tool window" flag (for activation tool window Run/Debug etc.)
   *
   * @param value if true, the tool window will be activated before launching this configuration.
   */
  void setActivateToolWindowBeforeRun(boolean value);

  /**
   * Returns the "Before launch: Activate tool window" flag (for activation tool window Run/Debug etc.)
   *
   * @return if true (it's default value), the tool window will be activated before launching this configuration.
   */
  boolean isActivateToolWindowBeforeRun();

  /**
   * Sets the "Single instance only" flag (meaning that only one instance of this run configuration can be run at the same time).
   *
   * @param singleton the "Single instance" flag.
   */
  void setSingleton(boolean singleton);

  /**
   * Returns the "Single instance only" flag (meaning that only one instance of this run configuration can be run at the same time).
   *
   * @return the "Single instance" flag.
   */
  boolean isSingleton();

  /**
   * Sets the name of the folder under which the configuration is displayed in the "Run/Debug Configurations" dialog.
   *
   * @param folderName the folder name, or null if the configuration is displayed on the top level.
   */
  void setFolderName(@Nullable String folderName);

  /**
   * Returns the name of the folder under which the configuration is displayed in the "Run/Debug Configurations" dialog.
   *
   * @return the folder name, or null if the configuration is displayed on the top level.
   */
  @Nullable String getFolderName();
}
