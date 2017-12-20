/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.externalSystem.settings;

import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.execution.ExternalTaskExecutionInfo;
import com.intellij.openapi.externalSystem.model.execution.ExternalTaskPojo;
import com.intellij.openapi.externalSystem.model.project.ExternalProjectBuildClasspathPojo;
import com.intellij.openapi.externalSystem.model.project.ExternalProjectPojo;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Holds local project-level external system-related settings (should be kept at the '*.iws' or 'workspace.xml').
 * <p/>
 * For example, we don't want to store recent tasks list at common external system settings, hence, that data
 * is kept at user-local settings (workspace settings).
 * <p/>
 * <b>Note:</b> non-abstract sub-classes of this class are expected to be marked by {@link State} annotation configured
 * to be stored under a distinct name at a workspace file.
 * 
 * @author Denis Zhdanov
 */
public abstract class AbstractExternalSystemLocalSettings<S extends AbstractExternalSystemLocalSettings.State> {
  protected S state;

  @NotNull private final ProjectSystemId myExternalSystemId;
  @NotNull private final Project myProject;

  protected AbstractExternalSystemLocalSettings(@NotNull ProjectSystemId externalSystemId, @NotNull Project project, @NotNull S state) {
    myExternalSystemId = externalSystemId;
    myProject = project;
    this.state = state;
  }

  protected AbstractExternalSystemLocalSettings(@NotNull ProjectSystemId externalSystemId, @NotNull Project project) {
    //noinspection unchecked
    this(externalSystemId, project, (S)new State());
  }

  /**
   * Asks current settings to drop all information related to external projects which root configs are located at the given paths.
   *
   * @param linkedProjectPathsToForget target root external project paths
   */
  public void forgetExternalProjects(@NotNull Set<String> linkedProjectPathsToForget) {
    Map<ExternalProjectPojo, Collection<ExternalProjectPojo>> projects = state.availableProjects;
    for (Iterator<Map.Entry<ExternalProjectPojo, Collection<ExternalProjectPojo>>> it = projects.entrySet().iterator(); it.hasNext(); ) {
      Map.Entry<ExternalProjectPojo, Collection<ExternalProjectPojo>> entry = it.next();
      if (linkedProjectPathsToForget.contains(entry.getKey().getPath())) {
        it.remove();
      }
    }

    for (Iterator<Map.Entry<String, Collection<ExternalTaskPojo>>> it = state.availableTasks.entrySet().iterator(); it.hasNext(); ) {
      Map.Entry<String, Collection<ExternalTaskPojo>> entry = it.next();
      if (linkedProjectPathsToForget.contains(entry.getKey())
          || linkedProjectPathsToForget.contains(ExternalSystemApiUtil.getRootProjectPath(entry.getKey(), myExternalSystemId, myProject))) {
        it.remove();
      }
    }

    if (!ContainerUtil.isEmpty(state.recentTasks)) {
      for (Iterator<ExternalTaskExecutionInfo> it = state.recentTasks.iterator(); it.hasNext(); ) {
        ExternalTaskExecutionInfo taskInfo = it.next();
        String path = taskInfo.getSettings().getExternalProjectPath();
        if (linkedProjectPathsToForget.contains(path) ||
            linkedProjectPathsToForget.contains(ExternalSystemApiUtil.getRootProjectPath(path, myExternalSystemId, myProject))) {
          it.remove();
        }
      }
    }

    for (Iterator<Map.Entry<String, ExternalProjectBuildClasspathPojo>> it = state.projectBuildClasspath.entrySet().iterator();
         it.hasNext(); ) {
      Map.Entry<String, ExternalProjectBuildClasspathPojo> entry = it.next();
      if (linkedProjectPathsToForget.contains(entry.getKey())
          || linkedProjectPathsToForget.contains(ExternalSystemApiUtil.getRootProjectPath(entry.getKey(), myExternalSystemId, myProject))) {
        it.remove();
      }
    }

    for (Iterator<Map.Entry<String, SyncType>> it = state.projectSyncType.entrySet().iterator(); it.hasNext(); ) {
      Map.Entry<String, SyncType> entry = it.next();
      if (linkedProjectPathsToForget.contains(entry.getKey())
          || linkedProjectPathsToForget.contains(ExternalSystemApiUtil.getRootProjectPath(entry.getKey(), myExternalSystemId, myProject))) {
        it.remove();
      }
    }

    Map<String, Long> modificationStamps = state.modificationStamps;
    for (String path : linkedProjectPathsToForget) {
      modificationStamps.remove(path);
    }
  }

  @NotNull
  public Map<ExternalProjectPojo, Collection<ExternalProjectPojo>> getAvailableProjects() {
    return state.availableProjects;
  }

  public void setAvailableProjects(@NotNull Map<ExternalProjectPojo, Collection<ExternalProjectPojo>> projects) {
    state.availableProjects = projects;
  }

  @NotNull
  public Map<String, Collection<ExternalTaskPojo>> getAvailableTasks() {
    return state.availableTasks;
  }

  public void setAvailableTasks(@NotNull Map<String, Collection<ExternalTaskPojo>> tasks) {
    state.availableTasks = tasks;
  }

  @NotNull
  public List<ExternalTaskExecutionInfo> getRecentTasks() {
    return ContainerUtil.notNullize(state.recentTasks);
  }

  @NotNull
  public Map<String, Long> getExternalConfigModificationStamps() {
    return state.modificationStamps;
  }

  @NotNull
  public Map<String, ExternalProjectBuildClasspathPojo> getProjectBuildClasspath() {
    return state.projectBuildClasspath;
  }

  @NotNull
  public Map<String, SyncType> getProjectSyncType() {
    return state.projectSyncType;
  }

  @Nullable
  public S getState() {
    return state;
  }

  public void loadState(@NotNull State state) {
    //noinspection unchecked
    this.state = (S)state;
    pruneOutdatedEntries();
  }

  private void pruneOutdatedEntries() {
    ExternalSystemManager<?, ?, ?, ?, ?> manager = ExternalSystemApiUtil.getManager(myExternalSystemId);
    assert manager != null;
    Set<String> pathsToForget = ContainerUtilRt.newHashSet();
    for (ExternalProjectPojo pojo : state.availableProjects.keySet()) {
      pathsToForget.add(pojo.getPath());
    }
    pathsToForget.addAll(state.availableTasks.keySet());
    for (ExternalTaskExecutionInfo taskInfo : ContainerUtil.notNullize(state.recentTasks)) {
      pathsToForget.add(taskInfo.getSettings().getExternalProjectPath());
    }

    AbstractExternalSystemSettings<?, ?, ?> settings = manager.getSettingsProvider().fun(myProject);
    for (ExternalProjectSettings projectSettings : settings.getLinkedProjectsSettings()) {
      pathsToForget.remove(projectSettings.getExternalProjectPath());
    }
    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      if (!ExternalSystemApiUtil.isExternalSystemAwareModule(myExternalSystemId, module)) continue;
      pathsToForget.remove(ExternalSystemApiUtil.getExternalProjectPath(module));
    }

    if (!pathsToForget.isEmpty()) {
      forgetExternalProjects(pathsToForget);
    }
  }

  public void setProjectBuildClasspath(Map<String, ExternalProjectBuildClasspathPojo> value) {
    state.projectBuildClasspath = value;
  }

  @Deprecated
  public void fillState(@NotNull State otherState) {
    otherState.recentTasks.clear();
    otherState.availableProjects = state.availableProjects;
    otherState.availableTasks = state.availableTasks;
    otherState.modificationStamps = state.modificationStamps;
    otherState.projectBuildClasspath = state.projectBuildClasspath;
    otherState.projectSyncType = state.projectSyncType;
  }

  public static class State {
    public final List<ExternalTaskExecutionInfo> recentTasks = new SmartList<>();
    public Map<ExternalProjectPojo, Collection<ExternalProjectPojo>> availableProjects = new THashMap<>();
    public Map<String/* project name */, Collection<ExternalTaskPojo>> availableTasks = new THashMap<>();

    public Map<String/* linked project path */, Long/* last config modification stamp */> modificationStamps = new THashMap<>();
    public Map<String/* linked project path */, ExternalProjectBuildClasspathPojo> projectBuildClasspath = new THashMap<>();
    public Map<String/* linked project path */, SyncType> projectSyncType = new THashMap<>();
  }

  public enum SyncType {
    PREVIEW, IMPORT, RE_IMPORT
  }
}
