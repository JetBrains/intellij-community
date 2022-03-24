// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution;

import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.ProjectExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import javax.swing.*;

/**
 * This class is responsible for auxiliary tasks (like build process or project-specific scripts execution)
 * that might be called just before run configuration start.
 * Exact list of tasks should be specified for every run configuration (or template) by user in dedicated UI.
 */

public abstract class BeforeRunTaskProvider<T extends BeforeRunTask<?>> {
  public static final ProjectExtensionPointName<BeforeRunTaskProvider<BeforeRunTask<?>>> EP_NAME =
    new ProjectExtensionPointName<>("com.intellij.stepsBeforeRunProvider");

  /**
   * @deprecated Use {@link #EP_NAME}
   */
  @Deprecated(forRemoval = true)
  public static final ExtensionPointName<BeforeRunTaskProvider<BeforeRunTask<?>>> EXTENSION_POINT_NAME =
    new ExtensionPointName<>("com.intellij.stepsBeforeRunProvider");

  public abstract Key<T> getId();

  public abstract @Nls(capitalization = Nls.Capitalization.Title) String getName();

  public @Nullable Icon getIcon() {
    return null;
  }

  public @Nls(capitalization = Nls.Capitalization.Sentence) String getDescription(T task) {
    return getName();
  }

  public @Nullable Icon getTaskIcon(T task) {
    return null;
  }

  public boolean isConfigurable() {
    return false;
  }

  /**
   * @return 'before run' task for the configuration or null, if the task from this provider is not applicable to the specified configuration.
   */
  public abstract @Nullable T createTask(@NotNull RunConfiguration runConfiguration);

  /**
   * @return {@code true} if task configuration is changed
   * @deprecated do not call directly, use {@link #configureTask(DataContext, RunConfiguration, BeforeRunTask)} instead.
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  public boolean configureTask(@NotNull RunConfiguration runConfiguration, @NotNull T task) {
    return false;
  }

  /**
   * @return {@code true} a promise returning true, if the task was changed.
   */
  public Promise<Boolean> configureTask(@NotNull DataContext context, @NotNull RunConfiguration configuration, @NotNull T task) {
    return Promises.resolvedPromise(configureTask(configuration, task));
  }

  public boolean canExecuteTask(@NotNull RunConfiguration configuration, @NotNull T task) {
    return true;
  }

  public abstract boolean executeTask(@NotNull DataContext context, @NotNull RunConfiguration configuration, @NotNull ExecutionEnvironment environment, @NotNull T task);

  /**
   * @return {@code true} if at most one task may be configured.
   */
  public boolean isSingleton() {
    // by default false because user can configure chain (java compile, generate something, java compile again)
    return false;
  }

  public static @Nullable <T extends BeforeRunTask<?>> BeforeRunTaskProvider<T> getProvider(@NotNull Project project, Key<T> key) {
    for (BeforeRunTaskProvider<BeforeRunTask<?>> provider : EP_NAME.getIterable(project)) {
      if (provider.getId() == key) {
        //noinspection unchecked
        return (BeforeRunTaskProvider<T>)provider;
      }
    }
    return null;
  }
}