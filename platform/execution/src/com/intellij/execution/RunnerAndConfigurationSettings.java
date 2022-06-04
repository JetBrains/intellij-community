// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution;

import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Describes a complete persisted run configuration (as displayed in the Run/Debug Configurations dialog), together with runner-specific
 * settings.
 *
 * @author anna
 * @see RunManager#createConfiguration(String, ConfigurationFactory)
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
   * Checks whether this run configuration is stored in a file (therefore, could be shared through VCS).
   * Note that there are 2 different ways of storing run configuration in a file: <code>.xml</code> file in <code>.idea/runConfigurations</code> folder (or <code>project.ipr</code> file)
   * and arbitrary <code>.run.xml</code> file anywhere within the project content.
   * Effectively, this method is equivalent to <code>isStoredInDotIdeaFolder() || isStoredInArbitraryFileInProject()</code>
   *
   * @see #isStoredInLocalWorkspace()
   * @see #isStoredInDotIdeaFolder()
   * @see #isStoredInArbitraryFileInProject()
   */
  default boolean isShared() {
    return isStoredInDotIdeaFolder() || isStoredInArbitraryFileInProject();
  }

  /**
   * @deprecated There are different ways of storing run configuration in a file,
   * use {@link #storeInLocalWorkspace()}, {@link #storeInDotIdeaFolder()} or {@link #storeInArbitraryFileInProject(String)}
   */
  @Deprecated(forRemoval = true)
  default void setShared(boolean value) {
    if (value) {
      storeInDotIdeaFolder();
    }
    else {
      storeInLocalWorkspace();
    }
  }

  /**
   * Make this run configuration impossible to share through VCS, store it in <code>.idea/workspace.xml</code> file.
   *
   * @see #storeInDotIdeaFolder()
   * @see #storeInArbitraryFileInProject(String)
   */
  void storeInLocalWorkspace();

  /**
   * Checks if this run configuration is not shared through VCS, i.e. stored it in <code>.idea/workspace.xml</code> file.
   *
   * @see #isStoredInDotIdeaFolder()
   * @see #isStoredInArbitraryFileInProject()
   */
  boolean isStoredInLocalWorkspace();

  /**
   * Make this run configuration possible to share through VCS: store it as an <code>.xml</code> file in <code>.idea/runConfigurations</code> folder (or <code>project.ipr</code> file).
   * Note that there are 2 different ways of storing run configuration in a file: <code>.xml</code> file in <code>.idea/runConfigurations</code> folder (or <code>project.ipr</code> file)
   * and arbitrary <code>.run.xml</code> file anywhere within the project content.
   *
   * @see #storeInArbitraryFileInProject(String)
   * @see #storeInLocalWorkspace()
   */
  void storeInDotIdeaFolder();

  /**
   * Checks if this run configuration is stored as an <code>.xml</code> file in <code>.idea/runConfigurations</code> folder (or <code>project.ipr</code> file).
   * Note that there are 2 different ways of storing run configuration in a file: <code>.xml</code> file in <code>.idea/runConfigurations</code> folder (or <code>project.ipr</code> file)
   * and arbitrary <code>.run.xml</code> file anywhere within the project content.
   *
   * @see #isStoredInArbitraryFileInProject()
   * @see #isStoredInLocalWorkspace()
   */
  boolean isStoredInDotIdeaFolder();

  /**
   * Make this run configuration possible to share through VCS: store it in a <code>.run.xml</code> file anywhere within the project content.
   * It's possible to store more than one run configuration in one <code>.run.xml</code> file.
   * Note that there are 2 different ways of storing run configuration in a file: <code>.xml</code> file in <code>.idea/runConfigurations</code> folder (or <code>project.ipr</code> file)
   * and arbitrary <code>.run.xml</code> file anywhere within the project content.
   *
   * @param filePath needs to be within the project content, otherwise the run configuration will be removed from the model.
   * @see #storeInDotIdeaFolder()
   * @see #storeInLocalWorkspace()
   */
  void storeInArbitraryFileInProject(@NonNls @NotNull String filePath);

  /**
   * Checks if this run configuration is stored in a <code>.run.xml</code> file within the project content.
   * Note that there are 2 different ways of storing run configuration in a file: <code>.xml</code> file in <code>.idea/runConfigurations</code> folder (or <code>project.ipr</code> file)
   * and arbitrary <code>.run.xml</code> file anywhere within the project content.
   *
   * @see #isStoredInDotIdeaFolder()
   * @see #isStoredInLocalWorkspace()
   */
  boolean isStoredInArbitraryFileInProject();

  /**
   * @return full path of the .run.xml file if {@link #isStoredInArbitraryFileInProject()} is <code>true</code>, <code>null</code> otherwise.
   */
  @Nullable
  @NonNls
  String getPathIfStoredInArbitraryFileInProject();

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
  void setName(@NlsSafe String name);

  /**
   * Returns the name of the configuration.
   *
   * @return the name of the configuration.
   */
  @NotNull
  @NlsSafe String getName();

  @NotNull
  String getUniqueID();

  /**
   * Returns the runner-managed settings for the specified runner.
   *
   * @param runner the runner for which the settings are requested.
   * @return the settings, or null if the runner doesn't provide any settings or the settings aren't configured for this configuration.
   */
  @Nullable <Settings extends RunnerSettings> Settings getRunnerSettings(@NotNull ProgramRunner<Settings> runner);

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
   *                                       but the execution should still be allowed
   * @throws RuntimeConfigurationError     if the configuration settings contain a fatal problem which makes it impossible to execute the run
   *                                       configuration.
   */
  default void checkSettings() throws RuntimeConfigurationException {
    checkSettings(null);
  }

  /**
   * Checks whether the run configuration settings are valid for execution with the specified executor.
   *
   * @param executor the executor which will be used to run the configuration, or null if the check is not specific to an executor.
   * @throws RuntimeConfigurationException if the configuration settings contain a non-fatal problem which the user should be warned about
   *                                       but the execution should still be allowed
   * @throws RuntimeConfigurationError     if the configuration settings contain a fatal problem which makes it impossible to execute the run
   *                                       configuration.
   */
  void checkSettings(@Nullable Executor executor) throws RuntimeConfigurationException;

  /**
   * Returns a factory object which can be used to create a copy of this configuration.
   *
   * @return copying factory instance
   */
  @NotNull
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
   * @deprecated Use {@link RunConfiguration#isAllowRunningInParallel()}
   */
  @Deprecated(forRemoval = true)
  default boolean isSingleton() {
    return !getConfiguration().isAllowRunningInParallel();
  }

  /**
   * @deprecated Use {@link RunConfiguration#setAllowRunningInParallel(boolean)}}
   */
  @Deprecated
  default void setSingleton(boolean value) {
    getConfiguration().setAllowRunningInParallel(!value);
  }

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

  @NlsSafe String toString();
}
