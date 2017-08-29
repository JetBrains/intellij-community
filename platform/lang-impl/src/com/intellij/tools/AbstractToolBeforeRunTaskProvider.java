/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.tools;

import com.intellij.execution.BeforeRunTaskProvider;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;

import javax.swing.*;

/**
 * @author traff
 */
public abstract class AbstractToolBeforeRunTaskProvider<T extends AbstractToolBeforeRunTask> extends BeforeRunTaskProvider<T> {
  protected static final Logger LOG = Logger.getInstance(ToolBeforeRunTaskProvider.class);

  @Override
  public Icon getIcon() {
    return AllIcons.General.ExternalToolsSmall;
  }

  @Override
  public boolean configureTask(RunConfiguration runConfiguration, T task) {
    final ToolSelectDialog dialog = new ToolSelectDialog(runConfiguration.getProject(), task.getToolActionId(), createToolsPanel());
    if (!dialog.showAndGet()) {
      return false;
    }
    boolean isModified = dialog.isModified();
    Tool selectedTool = dialog.getSelectedTool();
    if (selectedTool == null) {
      return true;
    }
    String selectedToolId = selectedTool.getActionId();
    String oldToolId = task.getToolActionId();
    if (oldToolId != null && oldToolId.equals(selectedToolId)) {
      return isModified;
    }
    task.setToolActionId(selectedToolId);
    return true;
  }

  protected abstract BaseToolsPanel createToolsPanel();

  @Override
  public boolean canExecuteTask(RunConfiguration configuration, T task) {
    return task.isExecutable();
  }

  @Override
  public String getDescription(T task) {
    final String actionId = task.getToolActionId();
    if (actionId == null) {
      LOG.error("Null id");
      return ToolsBundle.message("tools.unknown.external.tool");
    }
    Tool tool = task.findCorrespondingTool();
    if (tool == null) {
      return ToolsBundle.message("tools.unknown.external.tool");
    }
    String groupName = tool.getGroup();
    return ToolsBundle
      .message("tools.before.run.description", StringUtil.isEmpty(groupName) ? tool.getName() : groupName + "/" + tool.getName()) + (!tool.isEnabled() ? " (disabled)" : "");
  }

  @Override
  public boolean isConfigurable() {
    return true;
  }

  @Override
  public boolean executeTask(DataContext context, RunConfiguration configuration, ExecutionEnvironment env, T task) {
    if (!task.isExecutable()) {
      return false;
    }
    return task.execute(context, env.getExecutionId());
  }
}
