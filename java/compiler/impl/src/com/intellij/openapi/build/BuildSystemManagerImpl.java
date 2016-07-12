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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.intellij.util.containers.ContainerUtil.list;
import static com.intellij.util.containers.ContainerUtil.map;

/**
 * @author Vladislav.Soroka
 * @since 5/11/2016
 */
public class BuildSystemManagerImpl extends BuildSystemManager {

  private final BuildSystemDriver myDefaultBuildSystemDriver = new DefaultBuildSystemDriver();

  public BuildSystemManagerImpl(@NotNull Project project) {
    super(project);
  }

  @Override
  public void buildDirty(@NotNull Module[] modules, @Nullable BuildStatusNotification callback) {
    BuildScope buildScope = new BuildScopeImpl(map(list(modules), ModuleBuildTarget::new));
    doBuild(buildScope, true, callback);
  }

  @Override
  public void rebuild(@NotNull Module[] modules, @Nullable BuildStatusNotification callback) {
    BuildScope buildScope = new BuildScopeImpl(map(list(modules), ModuleBuildTarget::new));
    doBuild(buildScope, false, callback);
  }

  @Override
  public void compile(@NotNull VirtualFile[] files, @Nullable BuildStatusNotification callback) {
    List<ModuleFilesBuildTarget> buildTargets = Arrays.stream(files)
      .collect(Collectors.groupingBy(file -> ProjectFileIndex.SERVICE.getInstance(myProject).getModuleForFile(file, false)))
      .entrySet().stream()
      .map(entry -> new ModuleFilesBuildTarget(entry.getKey(), entry.getValue()))
      .collect(Collectors.toList());

    BuildScope buildScope = new BuildScopeImpl(buildTargets);
    doBuild(buildScope, false, callback);
  }

  @Override
  public void build(@NotNull Artifact[] artifacts, @Nullable BuildStatusNotification callback) {
    doBuild(artifacts, callback, true);
  }

  @Override
  public void rebuild(@NotNull Artifact[] artifacts, @Nullable BuildStatusNotification callback) {
    doBuild(artifacts, callback, false);
  }

  @Override
  public void buildDirty(@NotNull BuildScope scope, @Nullable BuildStatusNotification callback) {
    doBuild(scope, true, callback);
  }

  @Override
  public void rebuild(@NotNull BuildScope scope, @Nullable BuildStatusNotification callback) {
    doBuild(scope, false, callback);
  }

  @Override
  public void buildProjectDirty(@Nullable BuildStatusNotification callback) {
    doBuild(new ProjectBuildScope(myProject), true, callback);
  }

  @Override
  public void rebuildProject(@Nullable BuildStatusNotification callback) {
    doBuild(new ProjectBuildScope(myProject), false, callback);
  }

  @NotNull
  private static BuildSystemDriver[] getBuildDrivers() {
    return BuildSystemDriver.EP_NAME.getExtensions();
  }

  private void doBuild(@NotNull Artifact[] artifacts, @Nullable BuildStatusNotification callback, boolean isIncrementalBuild) {
    BuildScope buildScope = new BuildScopeImpl(map(list(artifacts), ArtifactBuildTarget::new));
    doBuild(buildScope, isIncrementalBuild, callback);
  }

  private void doBuild(@NotNull BuildScope scope, boolean isIncrementalBuild, @Nullable BuildStatusNotification callback) {
    Map<BuildSystemDriver, ? extends List<? extends BuildTarget>> toBuild =
      scope.getTargets().stream().collect(Collectors.groupingBy(buildTarget -> {
        for (BuildSystemDriver driver : getBuildDrivers()) {
          if (driver.canBuild(buildTarget)) return driver;
        }
        return myDefaultBuildSystemDriver;
      }));

    for (Map.Entry<BuildSystemDriver, ? extends List<? extends BuildTarget>> entry : toBuild.entrySet()) {
      BuildSystemDriver driver = entry.getKey();
      BuildScope buildScope = toBuild.size() == 1 ? scope : new BuildScopeImpl(entry.getValue(), scope.getSessionId());
      driver.build(new BuildContextImpl(myProject, buildScope, isIncrementalBuild), callback);
    }
  }

  private static class BuildContextImpl implements BuildContext {
    private final BuildScope myBuildScope;
    private final boolean myIsIncrementalBuild;
    private final Project myProject;

    public BuildContextImpl(Project project, BuildScope buildScope, boolean isIncrementalBuild) {
      myProject = project;
      myBuildScope = buildScope;
      myIsIncrementalBuild = isIncrementalBuild;
    }

    @Override
    public Project getProject() {
      return myProject;
    }

    @Override
    public BuildScope getScope() {
      return myBuildScope;
    }

    @Override
    public boolean isIncrementalBuild() {
      return myIsIncrementalBuild;
    }
  }
}
