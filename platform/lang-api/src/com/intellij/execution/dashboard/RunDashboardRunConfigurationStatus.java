// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.dashboard;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.treeView.WeighedItem;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Supplier;

/**
 * @author konstantin.aleev
 */
public class RunDashboardRunConfigurationStatus implements WeighedItem {
  public static final RunDashboardRunConfigurationStatus STARTED = new RunDashboardRunConfigurationStatus(
    "STARTED", ExecutionBundle.messagePointer("run.dashboard.started.group.name"), AllIcons.Actions.Execute, 10);
  public static final RunDashboardRunConfigurationStatus FAILED = new RunDashboardRunConfigurationStatus(
    "FAILED", ExecutionBundle.messagePointer("run.dashboard.failed.group.name"), AllIcons.General.Error, 20);
  public static final RunDashboardRunConfigurationStatus STOPPED = new RunDashboardRunConfigurationStatus(
    "STOPPED", ExecutionBundle.messagePointer("run.dashboard.stopped.group.name"), AllIcons.Actions.Restart, 30);
  public static final RunDashboardRunConfigurationStatus CONFIGURED = new RunDashboardRunConfigurationStatus(
    "CONFIGURED", ExecutionBundle.messagePointer("run.dashboard.configured.group.name"), AllIcons.General.Settings, 40);

  private final String myId;
  private final Supplier<@Nls String> myName;
  private final Icon myIcon;
  private final int myWeight;

  public RunDashboardRunConfigurationStatus(String id, Supplier<@Nls String> name, Icon icon, int weight) {
    myId = id;
    myName = name;
    myIcon = icon;
    myWeight = weight;
  }

  public String getId() {
    return myId;
  }

  public @Nls String getName() {
    return myName.get();
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

  public static @Nullable RunDashboardRunConfigurationStatus getStatusById(@NotNull String id) {
    return switch (id) {
      case "STARTED" -> STARTED;
      case "FAILED" -> FAILED;
      case "STOPPED" -> STOPPED;
      case "CONFIGURED" -> CONFIGURED;
      default -> null;
    };
  }
}
