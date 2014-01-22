/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.execution.runners;

import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows to postpone actual {@link RunProfileState} execution until all the needed preparations are done.
 * @param <Settings>
 */
public abstract class AsyncGenericProgramRunner<Settings extends RunnerSettings> implements ProgramRunner<Settings> {

  @Override
  @Nullable
  public Settings createConfigurationData(final ConfigurationInfoProvider settingsProvider) {
    return null;
  }

  @Override
  public void checkConfiguration(final RunnerSettings settings,
                                 final ConfigurationPerRunnerSettings configurationPerRunnerSettings) throws RuntimeConfigurationException {
  }

  @Override
  public void onProcessStarted(final RunnerSettings settings, final ExecutionResult executionResult) {
  }

  @Override
  @Nullable
  public SettingsEditor<Settings> getSettingsEditor(final Executor executor, final RunConfiguration configuration) {
    return null;
  }

  @Override
  public final void execute(@NotNull final ExecutionEnvironment environment) throws ExecutionException {
    execute(environment, null);
  }

  @Override
  public final void execute(@NotNull final ExecutionEnvironment env, @Nullable final ProgramRunner.Callback callback) throws ExecutionException {
    final Project project = env.getProject();
    final RunProfileState state = env.getState();
    if (state == null) {
      return;
    }
    RunManager.getInstance(project).refreshUsagesList(env.getRunProfile());
    prepare(project, env, state, new RunProfileStarterConsumer() {
      @Override
      public void consume(@NotNull RunProfileStarter starter) {
        startRunProfile(project, env, state, callback, starter);
      }
    });
  }

  /**
   * Makes all the needed preparations for the further execution. Although this method is called in EDT,
   * these preparations can be performed in a background thread. <p/>
   * Once the preparations are done, {@code consumer.consume(RunProfileStarter)} should be called in EDT to start actual execution.
   *
   * @param project Project instance
   * @param env ExecutionEnvironment instance
   * @param state RunProfileState instance
   * @param consumer RunProfileStarterConsumer instance; if no further actual execution is needed, then {@code consumer.consume} method shouldn't be called.
   */
  protected abstract void prepare(@NotNull Project project,
                                  @NotNull ExecutionEnvironment env,
                                  @NotNull RunProfileState state,
                                  @NotNull RunProfileStarterConsumer consumer) throws ExecutionException;

  public interface RunProfileStarterConsumer {
    void consume(@NotNull RunProfileStarter provider);
  }

  private static void startRunProfile(@NotNull Project project,
                                      @NotNull ExecutionEnvironment environment,
                                      @NotNull RunProfileState state,
                                      @Nullable final Callback callback,
                                      @NotNull final RunProfileStarter starter) {
    ExecutionManager.getInstance(project).startRunProfile(new RunProfileStarter() {
      @Override
      public RunContentDescriptor execute(@NotNull Project project,
                                          @NotNull Executor executor,
                                          @NotNull RunProfileState state,
                                          @Nullable RunContentDescriptor contentToReuse,
                                          @NotNull ExecutionEnvironment env) throws ExecutionException {
        RunContentDescriptor descriptor = starter.execute(project, executor, state, contentToReuse, env);
        if (descriptor != null) {
          descriptor.setExecutionId(env.getExecutionId());
        }
        if (callback != null) {
          callback.processStarted(descriptor);
        }
        return descriptor;
      }
    }, state, environment);
  }

}
