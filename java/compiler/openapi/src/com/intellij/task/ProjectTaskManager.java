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
package com.intellij.task;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides services to build project, modules, files or artifacts and execute Run Configuration.
 * <p>
 *
 * @author Vladislav.Soroka
 * @since 4/29/2016
 */
public abstract class ProjectTaskManager {
  protected final @NotNull Project myProject;

  public ProjectTaskManager(@NotNull Project project) {
    myProject = project;
  }

  public static ProjectTaskManager getInstance(Project project) {
    return ServiceManager.getService(project, ProjectTaskManager.class);
  }

  public abstract void run(@NotNull ProjectTask projectTask, @Nullable ProjectTaskNotification callback);

  public abstract void run(@NotNull ProjectTaskContext context,
                           @NotNull ProjectTask projectTask,
                           @Nullable ProjectTaskNotification callback);

  public abstract void buildAllModules(@Nullable ProjectTaskNotification callback);

  public void buildAllModules() {
    buildAllModules(null);
  }

  public abstract void rebuildAllModules(@Nullable ProjectTaskNotification callback);

  public void rebuildAllModules() {
    rebuildAllModules(null);
  }

  public abstract void build(@NotNull Module[] modules, @Nullable ProjectTaskNotification callback);

  public void build(@NotNull Module... modules) {
    build(modules, null);
  }

  public abstract void rebuild(@NotNull Module[] modules, @Nullable ProjectTaskNotification callback);

  public void rebuild(@NotNull Module... modules) {
    rebuild(modules, null);
  }

  public abstract void compile(@NotNull VirtualFile[] files, @Nullable ProjectTaskNotification callback);

  public void compile(@NotNull VirtualFile... files) {
    compile(files, null);
  }

  public abstract void build(@NotNull Artifact[] artifacts, @Nullable ProjectTaskNotification callback);

  public void build(@NotNull Artifact[] artifacts) {
    build(artifacts, null);
  }

  public abstract void rebuild(@NotNull Artifact[] artifacts, @Nullable ProjectTaskNotification callback);

  public void rebuild(@NotNull Artifact... artifacts) {
    rebuild(artifacts, null);
  }

  public abstract ProjectTask createAllModulesBuildTask(boolean isIncrementalBuild, Project project);

  public abstract ProjectTask createModulesBuildTask(boolean isIncrementalBuild, Module... modules);

  public abstract ProjectTask createArtifactsBuildTask(boolean isIncrementalBuild, Artifact... artifacts);

}
