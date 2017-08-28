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

import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class BeforeRunTaskProvider<T extends BeforeRunTask> {
  public static final ExtensionPointName<BeforeRunTaskProvider<BeforeRunTask>> EXTENSION_POINT_NAME =
    new ExtensionPointName<>("com.intellij.stepsBeforeRunProvider");

  public abstract Key<T> getId();

  public abstract String getName();

  @Nullable
  public Icon getIcon() {
    return null;
  }

  public String getDescription(T task) {
    return getName();
  }

  @Nullable
  public Icon getTaskIcon(T task) {
    return null;
  }

  public boolean isConfigurable() {
    return false;
  }

  /**
   * @return 'before run' task for the configuration or null, if the task from this provider is not applicable to the specified configuration 
   */
  @Nullable
  public abstract T createTask(@NotNull RunConfiguration runConfiguration);

  /**
   * @return {@code true} if task configuration is changed
   */
  public boolean configureTask(@NotNull RunConfiguration runConfiguration, @NotNull T task) {
    return false;
  }

  public boolean canExecuteTask(@NotNull RunConfiguration configuration, @NotNull T task) {
    return true;
  }

  public abstract boolean executeTask(DataContext context, RunConfiguration configuration, ExecutionEnvironment env, T task);

  /**
   *
   * @return {@code true} if at most one task may be configured
   */
  public boolean isSingleton() {
    return false;
  }

  @Nullable
  public static <T extends BeforeRunTask> BeforeRunTaskProvider<T> getProvider(Project project, Key<T> key) {
    BeforeRunTaskProvider<BeforeRunTask>[] providers = Extensions.getExtensions(EXTENSION_POINT_NAME, project);
    for (BeforeRunTaskProvider<BeforeRunTask> provider : providers) {
      if (provider.getId() == key) {
        //noinspection unchecked
        return (BeforeRunTaskProvider<T>)provider;
      }
    }
    return null;
  }
}