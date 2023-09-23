// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.dashboard;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.treeView.WeighedItem;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author konstantin.aleev
 */
public class RunDashboardRunConfigurationStatus implements WeighedItem {
  public static final RunDashboardRunConfigurationStatus STARTED = new RunDashboardRunConfigurationStatus(
    ExecutionBundle.message("run.dashboard.started.group.name"), AllIcons.Actions.Execute, 10);
  public static final RunDashboardRunConfigurationStatus FAILED = new RunDashboardRunConfigurationStatus(
    ExecutionBundle.message("run.dashboard.failed.group.name"), AllIcons.General.Error, 20);
  public static final RunDashboardRunConfigurationStatus STOPPED = new RunDashboardRunConfigurationStatus(
    ExecutionBundle.message("run.dashboard.stopped.group.name"), AllIcons.Actions.Restart, 30);
  public static final RunDashboardRunConfigurationStatus CONFIGURED = new RunDashboardRunConfigurationStatus(
    ExecutionBundle.message("run.dashboard.configured.group.name"), AllIcons.General.Settings, 40);

  private final @Nls String myName;
  private final Icon myIcon;
  private final int myWeight;

  public RunDashboardRunConfigurationStatus(@Nls String name, Icon icon, int weight) {
    myName = name;
    myIcon = icon;
    myWeight = weight;
  }

  public @Nls String getName() {
    return myName;
  }

  public Icon getIcon() {
    return myIcon;
  }

  @Override
  public int getWeight() {
    return myWeight;
  }

  public static @NotNull RunDashboardRunConfigurationStatus getStatus(RunDashboardRunConfigurationNode node) {
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
