// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import com.intellij.execution.Executor;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The manager of tabs in the Run/Debug toolwindows.
 *
 * @see com.intellij.execution.ExecutionManager#getContentManager()
 */
public interface RunContentManager {
  Topic<RunContentWithExecutorListener> TOPIC =
    Topic.create("Run Content", RunContentWithExecutorListener.class);

  static RunContentManager getInstance(Project project) {
    return ServiceManager.getService(project, RunContentManager.class);
  }

  /** @deprecated Use {@link LangDataKeys#RUN_CONTENT_DESCRIPTOR} instead (to be removed in IDEA 16) */
  @Deprecated @SuppressWarnings("UnusedDeclaration")
  DataKey<RunContentDescriptor> RUN_CONTENT_DESCRIPTOR = LangDataKeys.RUN_CONTENT_DESCRIPTOR;

  /**
   * Returns the content descriptor for the selected run configuration in the last activated Run/Debug toolwindow.
   *
   * @return the content descriptor, or null if there are no active run or debug configurations.
   */
  @Nullable
  RunContentDescriptor getSelectedContent();

  /**
   * Returns the content descriptor for the selected run configuration in the toolwindow corresponding to the specified executor.
   *
   * @param executor the executor (e.g. {@link com.intellij.execution.executors.DefaultRunExecutor#getRunExecutorInstance()} or
   *                 {@link com.intellij.execution.executors.DefaultDebugExecutor#getDebugExecutorInstance()})
   * @return the content descriptor, or null if there is no selected run configuration in the specified toolwindow.
   */
  @Nullable
  @Deprecated
  RunContentDescriptor getSelectedContent(Executor executor);

  /**
   * Returns the list of content descriptors for all currently displayed run/debug configurations.
   */
  @NotNull
  List<RunContentDescriptor> getAllDescriptors();

  /**
   * To reduce number of open contents RunContentManager reuses
   * some of them during showRunContent (for ex. if a process was stopped)
   */
  @Nullable
  RunContentDescriptor getReuseContent(@NotNull ExecutionEnvironment executionEnvironment);

  @Nullable
  RunContentDescriptor findContentDescriptor(Executor requestor, ProcessHandler handler);

  void showRunContent(@NotNull Executor executor, @NotNull RunContentDescriptor descriptor, @Nullable RunContentDescriptor contentToReuse);

  void showRunContent(@NotNull Executor executor, @NotNull RunContentDescriptor descriptor);

  void hideRunContent(@NotNull Executor executor, RunContentDescriptor descriptor);

  boolean removeRunContent(@NotNull Executor executor, @NotNull RunContentDescriptor descriptor);

  void toFrontRunContent(@NotNull Executor requestor, @NotNull RunContentDescriptor descriptor);

  void toFrontRunContent(@NotNull Executor requestor, @NotNull ProcessHandler handler);

  @Nullable
  ToolWindow getToolWindowByDescriptor(@NotNull RunContentDescriptor descriptor);

  void selectRunContent(@NotNull RunContentDescriptor descriptor);

  @Nullable
  @Deprecated
  default String getContentDescriptorToolWindowId(@Nullable RunnerAndConfigurationSettings settings) {
    return getContentDescriptorToolWindowId(settings != null ? settings.getConfiguration() : null);
  }

  /**
   * @return Tool window id where content should be shown. Null if content tool window is determined by executor.
   */
  @Nullable
  default String getContentDescriptorToolWindowId(@NotNull ExecutionEnvironment environment) {
    RunProfile runProfile = environment.getRunProfile();
    if (runProfile instanceof RunConfiguration) {
      return getContentDescriptorToolWindowId((RunConfiguration)runProfile);
    }

    RunnerAndConfigurationSettings settings = environment.getRunnerAndConfigurationSettings();
    if (settings != null) {
      return getContentDescriptorToolWindowId(settings.getConfiguration());
    }
    return null;
  }

  String getContentDescriptorToolWindowId(@Nullable RunConfiguration settings);

  @NotNull
  String getToolWindowIdByEnvironment(@NotNull ExecutionEnvironment executionEnvironment);
}
