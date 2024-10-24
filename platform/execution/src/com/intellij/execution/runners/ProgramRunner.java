// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.runners;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.util.NlsActions;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A ProgramRunner is responsible for the execution workflow of certain types of run configurations with a certain executor. For example,
 * one ProgramRunner can be responsible for debugging all Java-based run configurations (applications, JUnit tests, etc.); the run
 * configuration takes care of building a command line, and the program runner takes care of how exactly it needs to be executed.
 * <p>
 * A newly created program runner should be registered in a corresponding plugin.xml:
 * <pre>
 * &lt;extensions defaultExtensionNs="com.intellij"&gt;
 *   &lt;programRunner implementation="RunnerClassFQN"/&gt;
 * &lt;/extensions&gt;
 * </pre>
 * @see AsyncProgramRunner
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/execution.html">Execution (IntelliJ Platform Docs)</a>
 */
public interface ProgramRunner<Settings extends RunnerSettings> {
  ExtensionPointName<ProgramRunner<? extends RunnerSettings>> PROGRAM_RUNNER_EP = new ExtensionPointName<>("com.intellij.programRunner");

  interface Callback {
    void processStarted(RunContentDescriptor descriptor);

    /**
     * @deprecated Use {@link #processNotStarted(Throwable)}
     */
    @Deprecated
    default void processNotStarted() {}

    default void processNotStarted(@Nullable Throwable error) {
      processNotStarted();
    }
  }

  @Nullable
  static ProgramRunner<?> findRunnerById(@NotNull String id) {
    return PROGRAM_RUNNER_EP.findFirstSafe(it -> id.equals(it.getRunnerId()));
  }

  @Nullable
  static ProgramRunner<RunnerSettings> getRunner(@NotNull String executorId, @NotNull RunProfile settings) {
    //noinspection unchecked
    return (ProgramRunner<RunnerSettings>)PROGRAM_RUNNER_EP.findFirstSafe(it -> it.canRun(executorId, settings));
  }

  /**
   * Returns the unique ID of this runner. This ID is used to store settings and must not change between plugin versions.
   *
   * @return the program runner ID.
   */
  @NotNull
  @NonNls
  String getRunnerId();

  /**
   * Checks if the program runner is capable of running the specified configuration with the specified executor.
   *
   * @param executorId ID of the {@link Executor} with which the user is trying to run the configuration.
   * @param profile the configuration being run.
   * @return true if the runner can handle it, false otherwise.
   */
  boolean canRun(@NotNull String executorId, @NotNull RunProfile profile);

  /**
   * Creates a block of per-configuration settings used by this program runner.
   *
   * @param settingsProvider source of assorted information about the configuration being edited.
   * @return the per-runner settings, or null if this runner doesn't use any per-runner settings.
   */
  @Nullable
  default Settings createConfigurationData(@NotNull ConfigurationInfoProvider settingsProvider) {
    return null;
  }

  default void checkConfiguration(RunnerSettings settings, @Nullable ConfigurationPerRunnerSettings configurationPerRunnerSettings)
    throws RuntimeConfigurationException {
  }

  /**
   * @deprecated Not used by platform.
   */
  @SuppressWarnings("unused")
  @Deprecated
  default void onProcessStarted(RunnerSettings settings, ExecutionResult executionResult) {
  }

  default @Nullable @NlsActions.ActionText String getStartActionText(@NotNull Executor executor, @NotNull RunConfiguration configuration) {
    return null;
  }

  @Nullable
  default SettingsEditor<Settings> getSettingsEditor(Executor executor, RunConfiguration configuration) {
    return null;
  }

  void execute(@NotNull ExecutionEnvironment environment) throws ExecutionException;

  /**
   * @deprecated Use {@link #execute(ExecutionEnvironment)} and {@link ExecutionEnvironment#setCallback(Callback)}
   */
  @Deprecated
  default void execute(@NotNull ExecutionEnvironment environment, @Nullable Callback callback) throws ExecutionException {
    if (callback != null) {
      environment.setCallback(callback);
    }
    execute(environment);
  }
}