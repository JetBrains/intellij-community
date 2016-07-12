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
package com.intellij.openapi.build;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides services to build project, modules, files or artifacts.
 * <p>
 *
 * @author Vladislav.Soroka
 * @since 4/29/2016
 */
public abstract class BuildSystemManager {
  protected final @NotNull Project myProject;

  public BuildSystemManager(@NotNull Project project) {
    myProject = project;
  }

  public static BuildSystemManager getInstance(Project project) {
    return ServiceManager.getService(project, BuildSystemManager.class);
  }

  public abstract void buildProjectDirty(@Nullable BuildStatusNotification callback);

  public void buildProjectDirty() {
    buildProjectDirty(null);
  }

  public abstract void rebuildProject(@Nullable BuildStatusNotification callback);

  public void rebuildProject() {
    rebuildProject(null);
  }

  public abstract void buildDirty(@NotNull Module[] modules, @Nullable BuildStatusNotification callback);

  public void buildDirty(@NotNull Module... modules) {
    buildDirty(modules, null);
  }

  public abstract void rebuild(@NotNull Module[] modules, @Nullable BuildStatusNotification callback);

  public void rebuild(@NotNull Module... modules) {
    rebuild(modules, null);
  }

  public abstract void compile(@NotNull VirtualFile[] files, @Nullable BuildStatusNotification callback);

  public void compile(@NotNull VirtualFile... files) {
    compile(files, null);
  }

  public abstract void build(@NotNull Artifact[] artifacts, @Nullable BuildStatusNotification callback);

  public void build(@NotNull Artifact[] artifacts) {
    build(artifacts, null);
  }

  public abstract void rebuild(@NotNull Artifact[] artifacts, @Nullable BuildStatusNotification callback);

  public void rebuild(@NotNull Artifact... artifacts) {
    rebuild(artifacts, null);
  }

  public abstract void buildDirty(@NotNull BuildScope scope, @Nullable BuildStatusNotification callback);

  public abstract void rebuild(@NotNull BuildScope scope, @Nullable BuildStatusNotification callback);

  public abstract BuildScope createProjectBuildScope(Project project);

  public abstract BuildScope createModulesBuildScope(Module... modules);

  public abstract BuildScope createArtifactsBuildScope(Artifact... artifacts);
}
