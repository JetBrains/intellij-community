// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.settings;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.execution.ExternalTaskExecutionInfo;
import com.intellij.openapi.externalSystem.model.project.ExternalProjectBuildClasspathPojo;
import com.intellij.openapi.externalSystem.model.project.ExternalProjectPojo;
import com.intellij.openapi.externalSystem.settings.workspaceModel.ExternalProjectBuildClasspathEntity;
import com.intellij.openapi.externalSystem.settings.workspaceModel.ExternalProjectsBuildClasspathEntity;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.platform.backend.workspace.WorkspaceModel;
import com.intellij.platform.workspace.storage.MutableEntityStorage;
import com.intellij.util.SmartList;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import kotlin.sequences.Sequence;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

import static com.intellij.openapi.externalSystem.settings.workspaceModel.EntitiesKt.modifyEntity;
import static com.intellij.openapi.externalSystem.settings.workspaceModel.MappersKt.getExternalProjectBuildClasspathPojo;
import static com.intellij.openapi.externalSystem.settings.workspaceModel.MappersKt.getExternalProjectsBuildClasspathEntity;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.*;

/**
 * Holds local project-level external system-related settings (should be kept at the '*.iws' or 'workspace.xml').
 * <p/>
 * For example, we don't want to store recent tasks list at common external system settings, hence, that data
 * is kept at user-local settings.
 * <p/>
 * <b>Note:</b> non-abstract subclasses of this class are expected to be marked by {@link State} annotation configured
 * to be stored under a distinct name at a {@link StoragePathMacros#CACHE_FILE}.
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

    if (!ContainerUtil.isEmpty(state.recentTasks)) {
      for (Iterator<ExternalTaskExecutionInfo> it = state.recentTasks.iterator(); it.hasNext(); ) {
        ExternalTaskExecutionInfo taskInfo = it.next();
        String path = taskInfo.getSettings().getExternalProjectPath();
        if (linkedProjectPathsToForget.contains(path) ||
            linkedProjectPathsToForget.contains(getRootProjectPath(path, myExternalSystemId, myProject))) {
          it.remove();
        }
      }
    }

    forgetExternalProjectsClasspath(myExternalSystemId, myProject, linkedProjectPathsToForget);

    for (Iterator<Map.Entry<String, SyncType>> it = state.projectSyncType.entrySet().iterator(); it.hasNext(); ) {
      Map.Entry<String, SyncType> entry = it.next();
      if (linkedProjectPathsToForget.contains(entry.getKey())
          || linkedProjectPathsToForget.contains(getRootProjectPath(entry.getKey(), myExternalSystemId, myProject))) {
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
  public List<ExternalTaskExecutionInfo> getRecentTasks() {
    return ContainerUtil.notNullize(state.recentTasks);
  }

  @NotNull
  public Map<String, Long> getExternalConfigModificationStamps() {
    return state.modificationStamps;
  }

  @NotNull
  public Map<String, ExternalProjectBuildClasspathPojo> getProjectBuildClasspath() {
    return getExternalProjectsBuildClasspath(myProject);
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
    if (!state.projectBuildClasspath.isEmpty()) {
      saveProjectBuildClasspathWorkspaceEntity(myProject, state.projectBuildClasspath);
    }
    state.projectBuildClasspath = Collections.emptyMap();
    //noinspection unchecked
    this.state = (S)state;
    pruneOutdatedEntries();
  }

  public void invalidateCaches() {
    state.recentTasks.clear();
    state.availableProjects.clear();
    state.modificationStamps.clear();
    state.projectBuildClasspath.clear();
    state.projectSyncType.clear();
  }

  private void pruneOutdatedEntries() {
    ExternalSystemManager<?, ?, ?, ?, ?> manager = getManager(myExternalSystemId);
    assert manager != null;
    Set<String> pathsToForget = new HashSet<>();
    for (ExternalProjectPojo pojo : state.availableProjects.keySet()) {
      pathsToForget.add(pojo.getPath());
    }
    for (ExternalTaskExecutionInfo taskInfo : ContainerUtil.notNullize(state.recentTasks)) {
      pathsToForget.add(taskInfo.getSettings().getExternalProjectPath());
    }

    AbstractExternalSystemSettings<?, ?, ?> settings = manager.getSettingsProvider().fun(myProject);
    for (ExternalProjectSettings projectSettings : settings.getLinkedProjectsSettings()) {
      pathsToForget.remove(projectSettings.getExternalProjectPath());
    }
    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      if (!isExternalSystemAwareModule(myExternalSystemId, module)) continue;
      pathsToForget.remove(getExternalProjectPath(module));
    }

    if (!pathsToForget.isEmpty()) {
      forgetExternalProjects(pathsToForget);
    }
  }

  public void setProjectBuildClasspath(Map<String, ExternalProjectBuildClasspathPojo> value) {
    saveProjectBuildClasspathWorkspaceEntity(myProject, value);
  }

  @Deprecated(forRemoval = true)
  public void fillState(@NotNull State otherState) {
    otherState.recentTasks.clear();
    otherState.availableProjects = state.availableProjects;
    otherState.modificationStamps = state.modificationStamps;
    otherState.projectSyncType = state.projectSyncType;
  }

  public static class State {
    public final List<ExternalTaskExecutionInfo> recentTasks = new SmartList<>();
    public Map<ExternalProjectPojo, Collection<ExternalProjectPojo>> availableProjects = CollectionFactory.createSmallMemoryFootprintMap();
    public Map<String/* linked project path */, Long/* last config modification stamp */> modificationStamps = CollectionFactory.createSmallMemoryFootprintMap();
    @Deprecated(forRemoval = true, since = "24.2")
    public Map<String/* linked project path */, ExternalProjectBuildClasspathPojo> projectBuildClasspath = CollectionFactory.createSmallMemoryFootprintMap();
    public Map<String/* linked project path */, SyncType> projectSyncType = CollectionFactory.createSmallMemoryFootprintMap();
  }

  public enum SyncType {
    PREVIEW, IMPORT, RE_IMPORT
  }

  private static void forgetExternalProjectsClasspath(@NotNull ProjectSystemId myExternalSystemId,
                                                      @NotNull Project project,
                                                      @NotNull Set<String> linkedProjectPathsToForget) {
    modifyWorkspaceModel(
      project,
      ExternalSystemBundle.message("external.system.local.settings.workspace.model.project.unlink.title"),
      "ForgetExternalProjects update",
      storage -> {
        Sequence<ExternalProjectsBuildClasspathEntity> currentEntities = storage.entities(ExternalProjectsBuildClasspathEntity.class);
        Iterator<ExternalProjectsBuildClasspathEntity> entityIterator = currentEntities.iterator();
        if (!entityIterator.hasNext()) {
          return;
        }
        modifyEntity(storage, entityIterator.next(), modifiableEntity -> {
          Map<String, ExternalProjectBuildClasspathEntity> classpath = modifiableEntity.getProjectsBuildClasspath();
          Iterator<Map.Entry<String, ExternalProjectBuildClasspathEntity>> classpathIterator = classpath.entrySet().iterator();
          while (classpathIterator.hasNext()) {
            Map.Entry<String, ExternalProjectBuildClasspathEntity> entry = classpathIterator.next();
            if (linkedProjectPathsToForget.contains(entry.getKey()) ||
                linkedProjectPathsToForget.contains(getRootProjectPath(entry.getKey(), myExternalSystemId, project))) {
              classpathIterator.remove();
            }
          }
          return null;
        });
      });
  }

  private static void saveProjectBuildClasspathWorkspaceEntity(
    @NotNull Project project,
    @NotNull Map<String, ExternalProjectBuildClasspathPojo> projectBuildClasspath
  ) {
    modifyWorkspaceModel(
      project,
      ExternalSystemBundle.message("external.system.local.settings.workspace.model.project.update"),
      "AbstractExternalSystemLocalSettings update",
      storage -> {
        Sequence<ExternalProjectsBuildClasspathEntity> entities = storage.entities(ExternalProjectsBuildClasspathEntity.class);
        Iterator<ExternalProjectsBuildClasspathEntity> entityIterator = entities.iterator();
        ExternalProjectsBuildClasspathEntity.Builder newClasspathEntity = getExternalProjectsBuildClasspathEntity(projectBuildClasspath);
        if (!entityIterator.hasNext()) {
          storage.addEntity(newClasspathEntity);
        }
        else {
          modifyEntity(storage, entityIterator.next(), modifiableEntity -> {
            modifiableEntity.setProjectsBuildClasspath(newClasspathEntity.getProjectsBuildClasspath());
            return null;
          });
        }
      });
  }

  private static @NotNull Map<String, ExternalProjectBuildClasspathPojo> getExternalProjectsBuildClasspath(
    @NotNull Project project
  ) {
    Sequence<ExternalProjectsBuildClasspathEntity> entities = WorkspaceModel.getInstance(project)
      .getCurrentSnapshot()
      .entities(ExternalProjectsBuildClasspathEntity.class);
    Iterator<ExternalProjectsBuildClasspathEntity> entityIterator = entities.iterator();
    if (entityIterator.hasNext()) {
      return getExternalProjectBuildClasspathPojo(entityIterator.next());
    }
    return Collections.emptyMap();
  }

  private static void modifyWorkspaceModel(@NotNull Project project,
                                           @Nls String message,
                                           @NonNls String cause,
                                           @NotNull Consumer<MutableEntityStorage> mutator) {
    new Task.Modal(project, message, true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        WriteAction.runAndWait(() -> {
          WorkspaceModel.getInstance(project)
            .updateProjectModel(cause, storage -> {
              mutator.accept(storage);
              return null;
            });
        });
      }
    }.queue();
  }
}
