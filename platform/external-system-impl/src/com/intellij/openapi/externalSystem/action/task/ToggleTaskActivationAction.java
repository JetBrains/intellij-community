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
package com.intellij.openapi.externalSystem.action.task;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.externalSystem.action.ExternalSystemToggleAction;
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.model.task.TaskData;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration;
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManager;
import com.intellij.openapi.externalSystem.service.project.manage.ExternalSystemTaskActivator;
import com.intellij.openapi.externalSystem.view.ExternalSystemNode;
import com.intellij.openapi.externalSystem.view.RunConfigurationNode;
import com.intellij.openapi.externalSystem.view.TaskNode;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

import static com.intellij.openapi.externalSystem.service.project.manage.ExternalSystemTaskActivator.RUN_CONFIGURATION_TASK_PREFIX;

/**
 * @author Vladislav.Soroka
 * @since 10/28/2014
 */
public abstract class ToggleTaskActivationAction extends ExternalSystemToggleAction {

  private final ExternalSystemTaskActivator.Phase myPhase;

  protected ToggleTaskActivationAction(ExternalSystemTaskActivator.Phase phase) {
    myPhase = phase;
  }

  @Override
  protected boolean isEnabled(AnActionEvent e) {
    return super.isEnabled(e) && !getTasks(e).isEmpty();
  }

  @Override
  protected boolean doIsSelected(AnActionEvent e) {
    return hasTask(getTaskActivator(e), getTasks(e).get(0));
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    List<TaskData> tasks = getTasks(e);
    if (state) {
      addTasks(getTaskActivator(e), tasks);
    }
    else {
      removeTasks(getTaskActivator(e), tasks);
    }
  }

  @NotNull
  private static List<TaskData> getTasks(AnActionEvent e) {
    final List<ExternalSystemNode> selectedNodes = ExternalSystemDataKeys.SELECTED_NODES.getData(e.getDataContext());
    if (selectedNodes == null) return Collections.emptyList();

    List<TaskData> tasks = new SmartList<>();
    for (ExternalSystemNode node : selectedNodes) {
      if (node instanceof TaskNode && !node.isIgnored()) {
        tasks.add((TaskData)node.getData());
      }
      else if (node instanceof RunConfigurationNode) {
        final RunnerAndConfigurationSettings configurationSettings = ((RunConfigurationNode)node).getSettings();
        final ExternalSystemRunConfiguration runConfiguration = (ExternalSystemRunConfiguration)configurationSettings.getConfiguration();
        final ExternalSystemTaskExecutionSettings taskExecutionSettings = runConfiguration.getSettings();
        tasks.add(new TaskData(taskExecutionSettings.getExternalSystemId(), RUN_CONFIGURATION_TASK_PREFIX + configurationSettings.getName(),
                               taskExecutionSettings.getExternalProjectPath(), null));
      }
      else {
        return Collections.emptyList();
      }
    }
    return tasks;
  }

  protected boolean hasTask(ExternalSystemTaskActivator manager, TaskData taskData) {
    if (taskData == null) return false;
    return manager.isTaskOfPhase(taskData, myPhase);
  }

  private void addTasks(ExternalSystemTaskActivator taskActivator, List<TaskData> tasks) {
    taskActivator.addTasks(tasks, myPhase);
  }

  private void removeTasks(ExternalSystemTaskActivator taskActivator, List<TaskData> tasks) {
    taskActivator.removeTasks(tasks, myPhase);
  }


  private ExternalSystemTaskActivator getTaskActivator(AnActionEvent e) {
    return ExternalProjectsManager.getInstance(getProject(e)).getTaskActivator();
  }
}
