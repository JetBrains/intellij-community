// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution;

import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

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
   * @deprecated do not call directly, use {@link #configureTask(DataContext, RunConfiguration, BeforeRunTask)} instead
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  public boolean configureTask(@NotNull RunConfiguration runConfiguration, @NotNull T task) {
    return false;
  }

  /**
   * @return {@code true} a promise returning true, if the task was changed
   */
  public Promise<Boolean> configureTask(@NotNull DataContext context, @NotNull RunConfiguration configuration, @NotNull T task) {
    //noinspection deprecation
    return Promise.resolve(configureTask(configuration, task));
  }

  public boolean canExecuteTask(@NotNull RunConfiguration configuration, @NotNull T task) {
    return true;
  }

  public abstract boolean executeTask(DataContext context, @NotNull RunConfiguration configuration, @NotNull ExecutionEnvironment env, @NotNull T task);

  /**
   *
   * @return {@code true} if at most one task may be configured
   */
  public boolean isSingleton() {
    return false;
  }

  @Nullable
  public static <T extends BeforeRunTask> BeforeRunTaskProvider<T> getProvider(@NotNull Project project, Key<T> key) {
    for (BeforeRunTaskProvider<BeforeRunTask> provider : EXTENSION_POINT_NAME.getExtensionList(project)) {
      if (provider.getId() == key) {
        //noinspection unchecked
        return (BeforeRunTaskProvider<T>)provider;
      }
    }
    return null;
  }
}