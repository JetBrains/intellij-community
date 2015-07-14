/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem.service.project.manage;

import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.externalSystem.ExternalSystemUiAware;
import com.intellij.openapi.externalSystem.action.ExternalSystemAction;
import com.intellij.openapi.externalSystem.action.ExternalSystemActionUtil;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.execution.ExternalTaskExecutionInfo;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.task.TaskData;
import com.intellij.openapi.externalSystem.service.execution.AbstractExternalSystemTaskConfigurationType;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.keymap.KeymapExtension;
import com.intellij.openapi.keymap.KeymapGroup;
import com.intellij.openapi.keymap.KeymapGroupFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import gnu.trove.THashSet;
import icons.ExternalSystemIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * @author Vladislav.Soroka
 * @since 10/27/2014
 */
public class ExternalSystemKeymapExtension implements KeymapExtension {

  public KeymapGroup createGroup(Condition<AnAction> condition, Project project) {
    KeymapGroup result = KeymapGroupFactory.getInstance().createGroup(
      ExternalSystemBundle.message("external.system.keymap.group"), ExternalSystemIcons.TaskGroup);
    if (project == null) return result;

    MultiMap<Pair<ProjectSystemId, String>, String> projectToActionsMapping = MultiMap.create();

    ActionManager actionManager = ActionManager.getInstance();
    if (actionManager != null) {
      for (String eachId : actionManager.getActionIds(getActionPrefix(project, null))) {
        AnAction eachAction = actionManager.getAction(eachId);

        if (!(eachAction instanceof MyExternalSystemAction)) continue;
        if (condition != null && !condition.value(actionManager.getActionOrStub(eachId))) continue;

        MyExternalSystemAction taskAction = (MyExternalSystemAction)eachAction;
        projectToActionsMapping.putValue(Pair.create(taskAction.getSystemId(), taskAction.getGroup()), eachId);
      }
    }

    Map<ProjectSystemId, KeymapGroup> keymapGroupMap = ContainerUtil.newHashMap();
    for (Pair<ProjectSystemId, String> pair : projectToActionsMapping.keySet()) {
      if (!keymapGroupMap.containsKey(pair.first)) {
        final Icon projectIcon = ExternalSystemUiUtil.getUiAware(pair.first).getProjectIcon();
        KeymapGroup group = KeymapGroupFactory.getInstance().createGroup(pair.first.getReadableName(), projectIcon);
        result.addGroup(group);
        keymapGroupMap.put(pair.first, group);
      }
    }


    for (Map.Entry<Pair<ProjectSystemId, String>, Collection<String>> each : projectToActionsMapping.entrySet()) {
      String groupName = each.getKey().second;
      Collection<String> tasks = each.getValue();
      if (tasks.isEmpty()) continue;
      KeymapGroup group = KeymapGroupFactory.getInstance().createGroup(groupName, ExternalSystemIcons.TaskGroup);

      final KeymapGroup systemGroup = keymapGroupMap.get(each.getKey().first);
      if (systemGroup != null) {
        systemGroup.addGroup(group);
      }
      else {
        result.addGroup(group);
      }
      for (String actionId : tasks) {
        group.addActionId(actionId);
      }
    }

    return result;
  }

  public static void updateActions(Project project, Collection<DataNode<TaskData>> taskData) {
    clearActions(project, taskData);
    createActions(project, taskData);
  }

  private static void createActions(Project project, Collection<DataNode<TaskData>> taskData) {
    ActionManager manager = ActionManager.getInstance();
    if (manager != null) {
      for (DataNode<TaskData> each : taskData) {
        final DataNode<ModuleData> moduleData = ExternalSystemApiUtil.findParent(each, ProjectKeys.MODULE);
        if (moduleData == null || moduleData.isIgnored()) continue;
        ExternalSystemTaskAction eachAction = new ExternalSystemTaskAction(project, moduleData.getData().getInternalName(), each.getData());

        manager.unregisterAction(eachAction.getId());
        manager.registerAction(eachAction.getId(), eachAction);
      }
    }
  }

  public static void clearActions(Project project) {
    ActionManager manager = ActionManager.getInstance();
    if (manager != null) {
      for (String each : manager.getActionIds(getActionPrefix(project, null))) {
        manager.unregisterAction(each);
      }
    }
  }

  public static void clearActions(Project project, Collection<DataNode<TaskData>> taskData) {
    ActionManager manager = ActionManager.getInstance();
    if (manager != null) {
      for (DataNode<TaskData> each : taskData) {
        for (String eachAction : manager.getActionIds(getActionPrefix(project, each.getData().getLinkedExternalProjectPath()))) {
          manager.unregisterAction(eachAction);
        }
      }
    }
  }

  public static String getActionPrefix(@NotNull Project project, @Nullable String path) {
    return ExternalProjectsManager.getInstance(project).getShortcutsManager().getActionId(path, null);
  }

  public static void updateRunConfigurationActions(Project project, ProjectSystemId systemId) {
    final AbstractExternalSystemTaskConfigurationType configurationType = ExternalSystemUtil.findConfigurationType(systemId);
    if (configurationType == null) return;

    Set<RunnerAndConfigurationSettings> settings = new THashSet<RunnerAndConfigurationSettings>(
      RunManager.getInstance(project).getConfigurationSettingsList(configurationType));

    ActionManager manager = ActionManager.getInstance();
    if (manager != null) {
      for (RunnerAndConfigurationSettings configurationSettings : settings) {
        ExternalSystemRunConfigurationAction runConfigurationAction =
          new ExternalSystemRunConfigurationAction(project, configurationSettings);
        String id = runConfigurationAction.getId();
        manager.unregisterAction(id);
        manager.registerAction(id, runConfigurationAction);
      }
    }
  }

  private abstract static class MyExternalSystemAction extends ExternalSystemAction {
    public abstract String getId();

    public abstract String getGroup();

    public abstract ProjectSystemId getSystemId();
  }

  private static class ExternalSystemTaskAction extends MyExternalSystemAction {
    private final String myId;
    private final String myGroup;
    private final TaskData myTaskData;

    public ExternalSystemTaskAction(Project project, String group, TaskData taskData) {
      myGroup = group;
      myTaskData = taskData;
      myId = getActionPrefix(project, taskData.getLinkedExternalProjectPath()) + taskData.getName();

      Presentation template = getTemplatePresentation();
      template.setText(myTaskData.getName() + " (" + group + ")", false);
      template.setIcon(ExternalSystemIcons.Task);
    }

    @Override
    protected boolean isEnabled(AnActionEvent e) {
      return hasProject(e);
    }

    public void actionPerformed(@NotNull AnActionEvent e) {
      final ExternalTaskExecutionInfo taskExecutionInfo = ExternalSystemActionUtil.buildTaskInfo(myTaskData);
      ExternalSystemUtil.runTask(
        taskExecutionInfo.getSettings(), taskExecutionInfo.getExecutorId(), getProject(e), myTaskData.getOwner());
    }

    public TaskData getTaskData() {
      return myTaskData;
    }

    public String toString() {
      return myTaskData.toString();
    }

    @Override
    public String getGroup() {
      return myGroup;
    }

    @Override
    public ProjectSystemId getSystemId() {
      return myTaskData.getOwner();
    }

    @Override
    public String getId() {
      return myId;
    }
  }

  private static class ExternalSystemRunConfigurationAction extends MyExternalSystemAction {
    private final String myId;
    private final String myGroup;
    private final RunnerAndConfigurationSettings myConfigurationSettings;
    private final ProjectSystemId systemId;

    public ExternalSystemRunConfigurationAction(Project project, RunnerAndConfigurationSettings configurationSettings) {
      myConfigurationSettings = configurationSettings;
      ExternalSystemRunConfiguration runConfiguration = (ExternalSystemRunConfiguration)configurationSettings.getConfiguration();
      systemId = runConfiguration.getSettings().getExternalSystemId();

      ExternalSystemUiAware uiAware = ExternalSystemUiUtil.getUiAware(systemId);
      myGroup = uiAware.getProjectRepresentationName(runConfiguration.getSettings().getExternalProjectPath(), null);
      String actionIdPrefix = getActionPrefix(project, runConfiguration.getSettings().getExternalProjectPath());
      myId = actionIdPrefix + configurationSettings.getName();

      Presentation template = getTemplatePresentation();
      template.setText(myConfigurationSettings.getName(), false);
      template.setIcon(runConfiguration.getIcon());
    }

    @Override
    protected boolean isEnabled(AnActionEvent e) {
      return hasProject(e);
    }

    public void actionPerformed(@NotNull AnActionEvent e) {
      ProgramRunnerUtil.executeConfiguration(getProject(e), myConfigurationSettings, DefaultRunExecutor.getRunExecutorInstance());
    }

    public String toString() {
      return myConfigurationSettings.toString();
    }

    @Override
    public String getGroup() {
      return myGroup;
    }

    @Override
    public ProjectSystemId getSystemId() {
      return systemId;
    }

    public String getId() {
      return myId;
    }
  }
}

