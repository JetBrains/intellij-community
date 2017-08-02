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
package com.intellij.execution.dashboard;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * @author konstantin.aleev
 */
@ApiStatus.Experimental
public interface RunDashboardManager {
  Topic<DashboardListener> DASHBOARD_TOPIC =
    Topic.create("run dashboard", DashboardListener.class, Topic.BroadcastDirection.TO_PARENT);

  static RunDashboardManager getInstance(Project project) {
    return ServiceManager.getService(project, RunDashboardManager.class);
  }

  ContentManager getDashboardContentManager();

  String getToolWindowId();

  Icon getToolWindowIcon();

  boolean isToolWindowAvailable();

  void createToolWindowContent(@NotNull ToolWindow toolWindow);

  List<Pair<RunnerAndConfigurationSettings, RunContentDescriptor>> getRunConfigurations();

  boolean isShowConfigurations();

  void setShowConfigurations(boolean value);

  @Nullable
  RunDashboardAnimator getAnimator();

  boolean isShowInDashboard(@NotNull RunConfiguration runConfiguration);

  @Nullable
  RunDashboardContributor getContributor(@NotNull ConfigurationType type);
}
