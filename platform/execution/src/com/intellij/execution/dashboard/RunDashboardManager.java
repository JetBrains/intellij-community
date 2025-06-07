// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.dashboard;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import javax.swing.*;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * @author konstantin.aleev
 */
public interface RunDashboardManager {
  Topic<RunDashboardListener> DASHBOARD_TOPIC =
    Topic.create("run dashboard", RunDashboardListener.class, Topic.BroadcastDirection.TO_PARENT);

  static RunDashboardManager getInstance(@NotNull Project project) {
    return project.getService(RunDashboardManager.class);
  }

  ContentManager getDashboardContentManager();

  @NotNull
  String getToolWindowId();

  @NotNull
  Icon getToolWindowIcon();

  void updateDashboard(boolean withStructure);

  List<RunDashboardService> getRunConfigurations();

  boolean isShowInDashboard(@NotNull RunConfiguration runConfiguration);

  @NotNull
  @Unmodifiable
  Set<String> getTypes();

  void setTypes(Set<String> types);

  @NotNull
  Predicate<Content> getReuseCondition();

  interface RunDashboardService {
    @NotNull
    RunnerAndConfigurationSettings getSettings();

    @Nullable
    RunContentDescriptor getDescriptor();

    @Nullable
    Content getContent();
  }
}
