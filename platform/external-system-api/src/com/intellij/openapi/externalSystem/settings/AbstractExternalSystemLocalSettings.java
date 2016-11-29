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
package com.intellij.openapi.externalSystem.settings;

import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.execution.ExternalTaskExecutionInfo;
import com.intellij.openapi.externalSystem.model.execution.ExternalTaskPojo;
import com.intellij.openapi.externalSystem.model.project.ExternalProjectBuildClasspathPojo;
import com.intellij.openapi.externalSystem.model.project.ExternalProjectPojo;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.externalSystem.view.ExternalProjectsViewState;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

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
 * @since 4/4/13 4:51 PM
 */
public abstract class AbstractExternalSystemLocalSettings {
  private static final boolean PRESERVE_EXPAND_STATE
    = !SystemProperties.getBooleanProperty("external.system.forget.expand.nodes.state", false);

  private final AtomicReference<Map<String/*tree path*/, Boolean/*expanded*/>>                               myExpandStates
                                                                                                                                                =
    new AtomicReference<>(new HashMap<>());
  private final AtomicReference<List<ExternalTaskExecutionInfo>>                                             myRecentTasks                      =
    new AtomicReference<>(
      ContainerUtilRt.<ExternalTaskExecutionInfo>newArrayList()
    );
  private final AtomicReference<Map<ExternalProjectPojo, Collection<ExternalProjectPojo>>>                   myAvailableProjects                =
    new AtomicReference<>(
      ContainerUtilRt.<ExternalProjectPojo, Collection<ExternalProjectPojo>>newHashMap()
    );
  private final AtomicReference<Map<String/* external project config path */, Collection<ExternalTaskPojo>>> myAvailableTasks                   =
    new AtomicReference<>(
      ContainerUtilRt.<String, Collection<ExternalTaskPojo>>newHashMap()
    );
  private final AtomicReference<Map<String/* external project config path */, ExternalProjectBuildClasspathPojo>> myProjectBuildClasspath =
    new AtomicReference<>(
      ContainerUtilRt.<String, ExternalProjectBuildClasspathPojo>newHashMap()
    );
  private final AtomicReference<Map<String/* external project config path */, Long>>
                                                                                                             myExternalConfigModificationStamps =
    new AtomicReference<>(ContainerUtilRt.<String, Long>newHashMap());

  private final AtomicReference<ExternalProjectsViewState> myExternalProjectsViewState = new AtomicReference<>(
    new ExternalProjectsViewState()
  );

  @NotNull private final ProjectSystemId myExternalSystemId;
  @NotNull private final Project         myProject;

  protected AbstractExternalSystemLocalSettings(@NotNull ProjectSystemId externalSystemId,
                                                @NotNull Project project)
  {
    myExternalSystemId = externalSystemId;
    myProject = project;
  }

  /**
   * Asks current settings to drop all information related to external projects which root configs are located at the given paths.
   *
   * @param linkedProjectPathsToForget  target root external project paths
   */
  public void forgetExternalProjects(@NotNull Set<String> linkedProjectPathsToForget) {
    Map<ExternalProjectPojo, Collection<ExternalProjectPojo>> projects = myAvailableProjects.get();
    for (Iterator<Map.Entry<ExternalProjectPojo, Collection<ExternalProjectPojo>>> it = projects.entrySet().iterator(); it.hasNext(); ) {
      Map.Entry<ExternalProjectPojo, Collection<ExternalProjectPojo>> entry = it.next();
      if (linkedProjectPathsToForget.contains(entry.getKey().getPath())) {
        it.remove();
      }
    }

    for (Iterator<Map.Entry<String, Collection<ExternalTaskPojo>>> it = myAvailableTasks.get().entrySet().iterator(); it.hasNext(); ) {
      Map.Entry<String, Collection<ExternalTaskPojo>> entry = it.next();
      if (linkedProjectPathsToForget.contains(entry.getKey())
          || linkedProjectPathsToForget.contains(ExternalSystemApiUtil.getRootProjectPath(entry.getKey(), myExternalSystemId, myProject)))
      {
        it.remove();
      }
    }

    for (Iterator<ExternalTaskExecutionInfo> it = myRecentTasks.get().iterator(); it.hasNext(); ) {
      ExternalTaskExecutionInfo taskInfo = it.next();
      String path = taskInfo.getSettings().getExternalProjectPath();
      if (linkedProjectPathsToForget.contains(path) ||
          linkedProjectPathsToForget.contains(ExternalSystemApiUtil.getRootProjectPath(path, myExternalSystemId, myProject)))
      {
        it.remove();
      }
    }

    for (Iterator<Map.Entry<String, ExternalProjectBuildClasspathPojo>> it = myProjectBuildClasspath.get().entrySet().iterator(); it.hasNext(); ) {
      Map.Entry<String, ExternalProjectBuildClasspathPojo> entry = it.next();
      if (linkedProjectPathsToForget.contains(entry.getKey())
          || linkedProjectPathsToForget.contains(ExternalSystemApiUtil.getRootProjectPath(entry.getKey(), myExternalSystemId, myProject)))
      {
        it.remove();
      }
    }

    Map<String, Long> modificationStamps = myExternalConfigModificationStamps.get();
    for (String path : linkedProjectPathsToForget) {
      modificationStamps.remove(path);
    }
  }

  @SuppressWarnings("UnusedDeclaration")
  @NotNull
  public Map<String, Boolean> getExpandStates() { // Necessary for the serialization.
    return myExpandStates.get();
  }

  @NotNull
  public Map<ExternalProjectPojo, Collection<ExternalProjectPojo>> getAvailableProjects() {
    return myAvailableProjects.get();
  }

  public void setAvailableProjects(@NotNull Map<ExternalProjectPojo, Collection<ExternalProjectPojo>> projects) {
    myAvailableProjects.set(projects);
  }

  @NotNull
  public Map<String, Collection<ExternalTaskPojo>> getAvailableTasks() {
    return myAvailableTasks.get();
  }

  public void setAvailableTasks(@NotNull Map<String, Collection<ExternalTaskPojo>> tasks) {
    myAvailableTasks.set(tasks);
  }

  @NotNull
  public List<ExternalTaskExecutionInfo> getRecentTasks() {
    return myRecentTasks.get();
  }

  public void setRecentTasks(@NotNull List<ExternalTaskExecutionInfo> tasks) {
    myRecentTasks.set(tasks);
  }

  @NotNull
  public Map<String, Long> getExternalConfigModificationStamps() {
    return myExternalConfigModificationStamps.get();
  }

  @SuppressWarnings("UnusedDeclaration")
  public void setExternalConfigModificationStamps(@NotNull Map<String, Long> modificationStamps) {
    // Required for IJ serialization.
    myExternalConfigModificationStamps.set(modificationStamps);
  }

  @NotNull
  public Map<String, ExternalProjectBuildClasspathPojo> getProjectBuildClasspath() {
    return myProjectBuildClasspath.get();
  }

  @SuppressWarnings("UnusedDeclaration")
  public void setProjectBuildClasspath(@NotNull Map<String, ExternalProjectBuildClasspathPojo> projectsBuildClasspath) {
    // Required for IJ serialization.
    myProjectBuildClasspath.set(projectsBuildClasspath);
  }

  public ExternalProjectsViewState getExternalProjectsViewState() {
    return myExternalProjectsViewState.get();
  }

  @SuppressWarnings("UnusedDeclaration")
  public void setExternalProjectsViewState(ExternalProjectsViewState externalProjectsViewState) {
    // Required for IJ serialization.
    myExternalProjectsViewState.set(externalProjectsViewState);
  }

  public void fillState(@NotNull State state) {
    if (PRESERVE_EXPAND_STATE) {
      state.tasksExpandState = myExpandStates.get();
    }
    else {
      state.tasksExpandState = Collections.emptyMap();
    }
    state.recentTasks = myRecentTasks.get();
    state.availableProjects = myAvailableProjects.get();
    state.availableTasks = myAvailableTasks.get();
    state.modificationStamps = myExternalConfigModificationStamps.get();
    state.projectBuildClasspath = myProjectBuildClasspath.get();
    state.externalProjectsViewState = myExternalProjectsViewState.get();
  }

  public void loadState(@NotNull State state) {
    setIfNotNull(myExpandStates, state.tasksExpandState);
    setIfNotNull(myAvailableProjects, state.availableProjects);
    setIfNotNull(myAvailableTasks, state.availableTasks);
    setIfNotNull(myExternalConfigModificationStamps, state.modificationStamps);
    setIfNotNull(myProjectBuildClasspath, state.projectBuildClasspath);
    myExternalProjectsViewState.set(state.externalProjectsViewState);
    if (state.recentTasks != null) {
      List<ExternalTaskExecutionInfo> recentTasks = myRecentTasks.get();
      if (recentTasks != state.recentTasks) {
        recentTasks.clear();
        recentTasks.addAll(state.recentTasks);
      }
    }
    pruneOutdatedEntries();
  }

  private void pruneOutdatedEntries() {
    ExternalSystemManager<?,?,?,?,?> manager = ExternalSystemApiUtil.getManager(myExternalSystemId);
    assert manager != null;
    Set<String> pathsToForget = ContainerUtilRt.newHashSet();
    for (ExternalProjectPojo pojo : myAvailableProjects.get().keySet()) {
      pathsToForget.add(pojo.getPath());
    }
    for (String path : myAvailableTasks.get().keySet()) {
      pathsToForget.add(path);
    }
    for (ExternalTaskExecutionInfo taskInfo : myRecentTasks.get()) {
      pathsToForget.add(taskInfo.getSettings().getExternalProjectPath());
    }
    
    AbstractExternalSystemSettings<?, ?, ?> settings = manager.getSettingsProvider().fun(myProject);
    for (ExternalProjectSettings projectSettings : settings.getLinkedProjectsSettings()) {
      pathsToForget.remove(projectSettings.getExternalProjectPath());
    }
    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      String id = module.getOptionValue(ExternalSystemConstants.EXTERNAL_SYSTEM_ID_KEY);
      if (!myExternalSystemId.toString().equals(id)) {
        continue;
      }
      pathsToForget.remove(module.getOptionValue(ExternalSystemConstants.LINKED_PROJECT_PATH_KEY));
    }

    if (!pathsToForget.isEmpty()) {
      forgetExternalProjects(pathsToForget);
    }
  }

  protected static <K, V> void setIfNotNull(@NotNull AtomicReference<Map<K, V>> ref, @Nullable Map<K, V> candidate) {
    if (candidate == null) {
      return;
    }
    Map<K, V> map = ref.get();
    if (candidate != map) {
      map.clear();
      map.putAll(candidate);
    }
  }

  public static class State {
    public Map<String, Boolean>                                        tasksExpandState  = ContainerUtilRt.newHashMap();
    public List<ExternalTaskExecutionInfo>                             recentTasks       = ContainerUtilRt.newArrayList();
    public Map<ExternalProjectPojo, Collection<ExternalProjectPojo>>   availableProjects = ContainerUtilRt.newHashMap();
    public Map<String/* project name */, Collection<ExternalTaskPojo>> availableTasks    = ContainerUtilRt.newHashMap();

    public Map<String/* linked project path */, Long/* last config modification stamp */> modificationStamps
      = ContainerUtilRt.newHashMap();
    public Map<String/* linked project path */, ExternalProjectBuildClasspathPojo> projectBuildClasspath = ContainerUtilRt.newHashMap();
    public ExternalProjectsViewState externalProjectsViewState;
  }
}
