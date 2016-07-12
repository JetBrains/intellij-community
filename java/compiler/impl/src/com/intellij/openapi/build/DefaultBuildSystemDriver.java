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

import com.intellij.compiler.impl.ModuleCompileScope;
import com.intellij.execution.ExecutionTarget;
import com.intellij.execution.Executor;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationPerRunnerSettings;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.impl.ExecutionManagerImpl;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompileStatusNotification;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.impl.compiler.ArtifactCompileScope;
import com.intellij.packaging.impl.compiler.ArtifactsWorkspaceSettings;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Vladislav.Soroka
 * @since 5/11/2016
 */
public class DefaultBuildSystemDriver extends BuildSystemDriver {
  @Override
  public void build(@NotNull BuildContext buildContext, @Nullable BuildStatusNotification callback) {
    CompileStatusNotification compileNotification =
      callback == null ? null : (aborted, errors, warnings, compileContext) -> callback.finished(aborted, errors, warnings, buildContext);
    if (buildContext.getScope() instanceof ProjectBuildScope) {
      buildProject(buildContext, compileNotification);
    }
    else {
      Map<Class<? extends BuildTarget>, List<BuildTarget>> targetsMap =
        buildContext.getScope().getTargets().stream().collect(Collectors.groupingBy(BuildTarget::getClass));

      buildModulesTargets(buildContext, compileNotification, targetsMap);
      buildFilesTargets(buildContext, compileNotification, targetsMap);
      buildArtifactsTargets(buildContext, compileNotification, targetsMap);
    }
  }

  @Override
  public boolean canBuild(@NotNull BuildTarget buildTarget) {
    return true;
  }

  @Override
  public boolean canRun(@NotNull String executorId, @NotNull RunProfile runProfile) {
    return true;
  }

  @Override
  public ExecutionEnvironment createExecutionEnvironment(@NotNull RunProfile runProfile,
                                                         @NotNull Executor executor,
                                                         @NotNull ExecutionTarget executionTarget,
                                                         @NotNull Project project,
                                                         @Nullable RunnerSettings runnerSettings,
                                                         @Nullable ConfigurationPerRunnerSettings configurationSettings,
                                                         @Nullable RunnerAndConfigurationSettings settings) {
    return null;
  }

  private static void buildProject(BuildContext buildContext, CompileStatusNotification callback) {
    Project project = buildContext.getProject();
    if (buildContext.isIncrementalBuild()) {
      CompilerManager.getInstance(project).make(callback);
    }
    else {
      CompilerManager.getInstance(project).rebuild(callback);
    }
  }

  private static void buildModulesTargets(@NotNull BuildContext buildContext,
                                          @Nullable CompileStatusNotification compileNotification,
                                          @NotNull Map<Class<? extends BuildTarget>, List<BuildTarget>> targetsMap) {
    Project project = buildContext.getProject();
    Collection<? extends BuildTarget> buildTargets = targetsMap.get(ModuleBuildTarget.class);
    if (!ContainerUtil.isEmpty(buildTargets)) {
      Module[] modules = ContainerUtil.map2Array(buildTargets, Module.class, target -> ModuleBuildTarget.class.cast(target).getModule());
      if (buildContext.isIncrementalBuild()) {
        CompilerManager.getInstance(project).make(project, modules, compileNotification);
      }
      else {
        ModuleCompileScope compileScope = new ModuleCompileScope(project, modules, true);
        ExecutionManagerImpl.EXECUTION_SESSION_ID_KEY.set(compileScope, buildContext.getScope().getSessionId());
        CompilerManager.getInstance(project).compile(compileScope, compileNotification);
      }
    }
  }

  private static void buildFilesTargets(@NotNull BuildContext buildContext,
                                        @Nullable CompileStatusNotification compileNotification,
                                        @NotNull Map<Class<? extends BuildTarget>, List<BuildTarget>> targetsMap) {
    Collection<? extends BuildTarget> filesTargets = targetsMap.get(ModuleFilesBuildTarget.class);
    if (!ContainerUtil.isEmpty(filesTargets)) {
      VirtualFile[] files = filesTargets.stream()
        .flatMap(target -> Stream.of(ModuleFilesBuildTarget.class.cast(target).getFiles()))
        .toArray(VirtualFile[]::new);
      CompilerManager.getInstance(buildContext.getProject()).compile(files, compileNotification);
    }
  }

  private static void buildArtifactsTargets(@NotNull BuildContext buildContext,
                                            @Nullable CompileStatusNotification compileNotification,
                                            @NotNull Map<Class<? extends BuildTarget>, List<BuildTarget>> targetsMap) {
    Collection<? extends BuildTarget> artifactsTargets = targetsMap.get(ArtifactBuildTarget.class);
    if (!ContainerUtil.isEmpty(artifactsTargets)) {
      Project project = buildContext.getProject();
      Collection<Artifact> artifacts = artifactsTargets.stream()
        .map(target -> ArtifactBuildTarget.class.cast(target).getArtifact())
        .collect(Collectors.toList());

      final CompileScope scope = ArtifactCompileScope.createArtifactsScope(project, artifacts, !buildContext.isIncrementalBuild());
      ArtifactsWorkspaceSettings.getInstance(project).setArtifactsToBuild(artifacts);
      ExecutionManagerImpl.EXECUTION_SESSION_ID_KEY.set(scope, buildContext.getScope().getSessionId());
      //in external build we can set 'rebuild' flag per target type
      CompilerManager.getInstance(project).make(scope, compileNotification);
    }
  }
}
