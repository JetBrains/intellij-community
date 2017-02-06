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
package com.intellij.execution.dashboard.actions;

import com.intellij.execution.ExecutionManager;
import com.intellij.execution.ExecutionTargetManager;
import com.intellij.execution.Executor;
import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.dashboard.DashboardRunConfigurationNode;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.RunContentDescriptor;

import javax.swing.*;

/**
 * @author konstantin.aleev
 */
public abstract class ExecutorAction extends RuntimeDashboardTreeLeafAction<DashboardRunConfigurationNode> {
  protected ExecutorAction(String text, String description, Icon icon) {
    super(text, description, icon);
  }

  @Override
  protected boolean isEnabled4(DashboardRunConfigurationNode node) {
    String executorId = getExecutor().getId();
    ProgramRunner runner = ProgramRunnerUtil.getRunner(executorId, node.getConfigurationSettings());
    return runner != null && runner.canRun(executorId, node.getConfigurationSettings().getConfiguration());
  }

  @Override
  protected void doActionPerformed(DashboardRunConfigurationNode node) {
    RunContentDescriptor descriptor = node.getDescriptor();
    ExecutionManager.getInstance(node.getProject()).restartRunProfile(node.getProject(),
                                                                      getExecutor(),
                                                                      ExecutionTargetManager.getActiveTarget(node.getProject()),
                                                                      node.getConfigurationSettings(),
                                                                      descriptor == null ? null : descriptor.getProcessHandler());
  }

  @Override
  protected Class<DashboardRunConfigurationNode> getTargetNodeClass() {
    return DashboardRunConfigurationNode.class;
  }

  protected abstract Executor getExecutor();
}
