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

import javax.swing.*;

/**
 * @author konstantin.aleev
 */
public class DashboardRunConfigurationStatus {
  public static final DashboardRunConfigurationStatus STARTED = new DashboardRunConfigurationStatus(
    ExecutionBundle.message("run.dashboard.started.group.name"), AllIcons.Toolwindows.ToolWindowRun);
  public static final DashboardRunConfigurationStatus STOPPED = new DashboardRunConfigurationStatus(
    ExecutionBundle.message("run.dashboard.stopped.group.name"), AllIcons.Actions.Suspend);
  public static final DashboardRunConfigurationStatus FAILED = new DashboardRunConfigurationStatus(
    ExecutionBundle.message("run.dashboard.failed.group.name"), AllIcons.General.Error);

  private final String myName;
  private final Icon myIcon;

  public DashboardRunConfigurationStatus(String name, Icon icon) {
    myName = name;
    myIcon = icon;
  }

  public String getName() {
    return myName;
  }

  public Icon getIcon() {
    return myIcon;
  }

  public static DashboardRunConfigurationStatus getStatus(DashboardRunConfigurationNode node) {
    RunContentDescriptor descriptor = node.getDescriptor();
    if (descriptor == null) {
      return STOPPED;
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
