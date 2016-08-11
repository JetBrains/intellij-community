/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.task.TaskData;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.externalSystem.view.ExternalProjectsView;
import com.intellij.openapi.externalSystem.view.ExternalProjectsViewImpl;
import com.intellij.openapi.externalSystem.view.ExternalProjectsViewState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.Function;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.openapi.externalSystem.model.ProjectKeys.MODULE;
import static com.intellij.openapi.externalSystem.model.ProjectKeys.TASK;

/**
 * @author Vladislav.Soroka
 * @since 10/23/2014
 */
@State(name = "ExternalProjectsManager", storages = {@Storage(StoragePathMacros.WORKSPACE_FILE)})
public class ExternalProjectsManager implements PersistentStateComponent<ExternalProjectsState>, Disposable {
  private static final Logger LOG = Logger.getInstance(ExternalProjectsManager.class);

  private final AtomicBoolean isInitialized = new AtomicBoolean();
  @NotNull
  private ExternalProjectsState myState = new ExternalProjectsState();

  @NotNull
  private final Project myProject;
  private final ExternalSystemRunManagerListener myRunManagerListener;
  private final ExternalSystemTaskActivator myTaskActivator;
  private final ExternalSystemShortcutsManager myShortcutsManager;
  private final List<ExternalProjectsView> myProjectsViews = new SmartList<>();


  public static ExternalProjectsManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, ExternalProjectsManager.class);
  }

  public ExternalProjectsManager(@NotNull Project project) {
    myProject = project;
    myShortcutsManager = new ExternalSystemShortcutsManager(project);
    Disposer.register(this, myShortcutsManager);
    myTaskActivator = new ExternalSystemTaskActivator(project);
    myRunManagerListener = new ExternalSystemRunManagerListener(this);
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  public ExternalSystemShortcutsManager getShortcutsManager() {
    return myShortcutsManager;
  }

  public ExternalSystemTaskActivator getTaskActivator() {
    return myTaskActivator;
  }

  public void registerView(@NotNull ExternalProjectsView externalProjectsView) {
    assert getExternalProjectsView(externalProjectsView.getSystemId()) == null;

    init();
    myProjectsViews.add(externalProjectsView);
    if (externalProjectsView instanceof ExternalProjectsViewImpl) {
      ExternalProjectsViewImpl view = (ExternalProjectsViewImpl)externalProjectsView;
      view.loadState(myState.getExternalSystemsState().get(externalProjectsView.getSystemId().getId()).getProjectsViewState());
      view.init();
    }
  }

  @Nullable
  public ExternalProjectsView getExternalProjectsView(@NotNull ProjectSystemId systemId) {
    for (ExternalProjectsView projectsView : myProjectsViews) {
      if(projectsView.getSystemId().equals(systemId)) return projectsView;
    }
    return null;
  }

  public void init() {
    synchronized (isInitialized) {
      if (isInitialized.getAndSet(true)) return;

      // load external projects data
      ExternalProjectsDataStorage.getInstance(myProject).load();
      myRunManagerListener.attach();

      // init shortcuts manager
      myShortcutsManager.init();
      for (ExternalSystemManager<?, ?, ?, ?, ?> systemManager : ExternalSystemApiUtil.getAllManagers()) {
        final Collection<ExternalProjectInfo> externalProjects =
          ExternalProjectsDataStorage.getInstance(myProject).list(systemManager.getSystemId());
        for (ExternalProjectInfo externalProject : externalProjects) {
          if (externalProject.getExternalProjectStructure() == null) continue;
          Collection<DataNode<TaskData>> taskData =
            ExternalSystemApiUtil.findAllRecursively(externalProject.getExternalProjectStructure(), TASK);
          myShortcutsManager.scheduleKeymapUpdate(taskData);
        }

        if (!externalProjects.isEmpty()) {
          myShortcutsManager.scheduleRunConfigurationKeymapUpdate(systemManager.getSystemId());
        }
      }
      // init task activation info
      myTaskActivator.init();
    }
  }

  public void updateExternalProjectData(ExternalProjectInfo externalProject) {
    // update external projects data
    ExternalProjectsDataStorage.getInstance(myProject).update(externalProject);

    // update shortcuts manager
    if (externalProject.getExternalProjectStructure() != null) {
      final ProjectData projectData = externalProject.getExternalProjectStructure().getData();

      ExternalSystemUtil.scheduleExternalViewStructureUpdate(myProject, projectData.getOwner());

      Collection<DataNode<TaskData>> taskData =
        ExternalSystemApiUtil.findAllRecursively(externalProject.getExternalProjectStructure(), TASK);
      myShortcutsManager.scheduleKeymapUpdate(taskData);
      myShortcutsManager.scheduleRunConfigurationKeymapUpdate(projectData.getOwner());
    }
  }

  public void forgetExternalProjectData(@NotNull ProjectSystemId projectSystemId, @NotNull String linkedProjectPath) {
    ExternalProjectsDataStorage.getInstance(myProject).remove(projectSystemId, linkedProjectPath);
    ExternalSystemUtil.scheduleExternalViewStructureUpdate(myProject, projectSystemId);
  }

  @NotNull
  @Override
  public ExternalProjectsState getState() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    for (ExternalProjectsView externalProjectsView : myProjectsViews) {
      if (externalProjectsView instanceof ExternalProjectsViewImpl) {
        final ExternalProjectsViewState externalProjectsViewState = ((ExternalProjectsViewImpl)externalProjectsView).getState();
        final ExternalProjectsState.State state = myState.getExternalSystemsState().get(externalProjectsView.getSystemId().getId());
        assert state != null;
        state.setProjectsViewState(externalProjectsViewState);
      }
    }
    return myState;
  }

  @NotNull
  public ExternalProjectsStateProvider getStateProvider() {
    return new ExternalProjectsStateProvider() {
      @Override
      public List<TasksActivation> getAllTasksActivation() {
        List<TasksActivation> result = new SmartList<>();
        for (Map.Entry<String, ExternalProjectsState.State> systemState : myState.getExternalSystemsState().entrySet()) {
          ProjectSystemId systemId = new ProjectSystemId(systemState.getKey());
          for (Map.Entry<String, TaskActivationState> activationStateEntry : systemState.getValue().getExternalSystemsTaskActivation()
            .entrySet()) {
            result.add(new TasksActivation(systemId, activationStateEntry.getKey(), activationStateEntry.getValue()));
          }
        }

        return result;
      }

      @Override
      public List<TasksActivation> getTasksActivation(@NotNull final ProjectSystemId systemId) {
        final Set<Map.Entry<String, TaskActivationState>> entries =
          myState.getExternalSystemsState().get(systemId.getId()).getExternalSystemsTaskActivation().entrySet();
        return ContainerUtil.map(entries, entry -> new TasksActivation(systemId, entry.getKey(), entry.getValue()));
      }

      @Override
      public TaskActivationState getTasksActivation(@NotNull ProjectSystemId systemId, @NotNull String projectPath) {
        return myState.getExternalSystemsState().get(systemId.getId()).getExternalSystemsTaskActivation().get(projectPath);
      }

      @Override
      public Map<String, TaskActivationState> getProjectsTasksActivationMap(@NotNull final ProjectSystemId systemId) {
        return myState.getExternalSystemsState().get(systemId.getId()).getExternalSystemsTaskActivation();
      }
    };
  }

  public boolean isIgnored(@NotNull ProjectSystemId systemId, @NotNull String projectPath) {
    final ExternalProjectInfo projectInfo = ExternalSystemUtil.getExternalProjectInfo(myProject, systemId, projectPath);
    if (projectInfo == null) return true;

    return ExternalProjectsDataStorage.getInstance(myProject).isIgnored(projectInfo.getExternalProjectPath(), projectPath, MODULE);
  }

  public void setIgnored(@NotNull DataNode<?> dataNode, boolean isIgnored) {
    ExternalProjectsDataStorage.getInstance(myProject).setIgnored(dataNode, isIgnored);
    ExternalSystemKeymapExtension.updateActions(myProject, ExternalSystemApiUtil.findAllRecursively(dataNode, TASK));
  }

  @Override
  public void loadState(ExternalProjectsState state) {
    myState = state == null ? new ExternalProjectsState() : state;
  }

  @Override
  public void dispose() {
    myProjectsViews.clear();
    myRunManagerListener.detach();
  }

  public interface ExternalProjectsStateProvider {
    class TasksActivation {
      public final ProjectSystemId systemId;
      public final String projectPath;
      public final TaskActivationState state;

      public TasksActivation(ProjectSystemId systemId,
                             String projectPath,
                             TaskActivationState state) {
        this.systemId = systemId;
        this.projectPath = projectPath;
        this.state = state;
      }
    }

    List<TasksActivation> getAllTasksActivation();

    List<TasksActivation> getTasksActivation(@NotNull ProjectSystemId systemId);

    TaskActivationState getTasksActivation(@NotNull ProjectSystemId systemId, @NotNull String projectPath);

    Map<String, TaskActivationState> getProjectsTasksActivationMap(@NotNull ProjectSystemId systemId);
  }
}
