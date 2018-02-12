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

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.icons.AllIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author konstantin.aleev
 */
public class RunDashboardRunConfigurationStatus {
  public static final RunDashboardRunConfigurationStatus STARTED = new RunDashboardRunConfigurationStatus(
    ExecutionBundle.message("run.dashboard.started.group.name"), AllIcons.Actions.Execute, 10);
  public static final RunDashboardRunConfigurationStatus FAILED = new RunDashboardRunConfigurationStatus(
    ExecutionBundle.message("run.dashboard.failed.group.name"), AllIcons.General.Error, 20);
  public static final RunDashboardRunConfigurationStatus STOPPED = new RunDashboardRunConfigurationStatus(
    ExecutionBundle.message("run.dashboard.stopped.group.name"), AllIcons.Actions.Restart, 30);
  public static final RunDashboardRunConfigurationStatus CONFIGURED = new RunDashboardRunConfigurationStatus(
    ExecutionBundle.message("run.dashboard.configured.group.name"), AllIcons.General.Settings, 40);

  private final String myName;
  private final Icon myIcon;
  private final int myPriority;

  public RunDashboardRunConfigurationStatus(String name, Icon icon, int priority) {
    myName = name;
    myIcon = icon;
    myPriority = priority;
  }

  public String getName() {
    return myName;
  }

  public Icon getIcon() {
    return myIcon;
  }

  public int getPriority() {
    return myPriority;
  }

  @NotNull
  public static RunDashboardRunConfigurationStatus getStatus(RunDashboardRunConfigurationNode node) {
    RunContentDescriptor descriptor = node.getDescriptor();
    if (descriptor == null) {
      return CONFIGURED;
    }
    ProcessHandler processHandler = descriptor.getProcessHandler();
    if (processHandler == null) {
      return STOPPED;
    }
    Integer exitCode = processHandler.getExitCode();
    if (exitCode == null) {
      return STARTED;
    }
    Boolean terminationRequested = processHandler.getUserData(ProcessHandler.TERMINATION_REQUESTED);
    if (exitCode == 0 || (terminationRequested != null && terminationRequested)) {
      return STOPPED;
    }
    return FAILED;
  }
}
