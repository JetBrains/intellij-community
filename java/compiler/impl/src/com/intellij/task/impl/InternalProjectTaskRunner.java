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
package com.intellij.task.impl;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunConfiguration;
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
import com.intellij.task.*;
import com.intellij.util.SmartList;
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
public class InternalProjectTaskRunner extends ProjectTaskRunner {
  @Override
  public void run(@NotNull Project project,
                  @NotNull ProjectTaskContext context,
                  @Nullable ProjectTaskNotification callback,
                  @NotNull Collection<? extends ProjectTask> tasks) {
    CompileStatusNotification compileNotification =
      callback == null ? null : (aborted, errors, warnings, compileContext) ->
        callback.finished(new ProjectTaskResult(aborted, errors, warnings));

    Map<Class<? extends ProjectTask>, List<ProjectTask>> taskMap = groupBy(tasks);
    runModulesBuildTasks(project, context, compileNotification, taskMap);
    runFilesBuildTasks(project, compileNotification, taskMap);
    runArtifactsBuildTasks(project, context, compileNotification, taskMap);
  }

  @Override
  public boolean canRun(@NotNull ProjectTask projectTask) {
    return true;
  }

  @Override
  public ExecutionEnvironment createExecutionEnvironment(@NotNull Project project,
                                                         @NotNull ExecuteRunConfigurationTask task,
                                                         @Nullable Executor executor) {
    return null;
  }

  public static Map<Class<? extends ProjectTask>, List<ProjectTask>> groupBy(@NotNull Collection<? extends ProjectTask> tasks) {
    return tasks.stream().collect(Collectors.groupingBy(o -> {
      if (o instanceof ModuleFilesBuildTask) return ModuleFilesBuildTask.class;
      if (o instanceof ModuleBuildTask) return ModuleBuildTask.class;
      if (o instanceof ArtifactBuildTask) return ArtifactBuildTask.class;
      return o.getClass();
    }));
  }

  private static void runModulesBuildTasks(@NotNull Project project,
                                           @NotNull ProjectTaskContext context,
                                           @Nullable CompileStatusNotification compileNotification,
                                           @NotNull Map<Class<? extends ProjectTask>, List<ProjectTask>> tasksMap) {
    Collection<? extends ProjectTask> buildTasks = tasksMap.get(ModuleBuildTask.class);


    if (!ContainerUtil.isEmpty(buildTasks)) {
      List<Module> toMake = new SmartList<>();
      List<Module> toCompile = new SmartList<>();

      for (ProjectTask buildProjectTask : buildTasks) {
        ModuleBuildTask moduleBuildTask = (ModuleBuildTask)buildProjectTask;

        if (moduleBuildTask.isIncrementalBuild()) {
          toMake.add(moduleBuildTask.getModule());
        }
        else {
          toCompile.add(moduleBuildTask.getModule());
        }
      }
      CompilerManager compilerManager = CompilerManager.getInstance(project);
      if (!toMake.isEmpty()) {
        CompileScope scope = createScope(project, compilerManager, context, toMake);
        // TODO handle multiple notifications
        compilerManager.make(scope, compileNotification);
      }
      if (!toCompile.isEmpty()) {
        CompileScope scope = createScope(project, compilerManager, context, toCompile);
        // TODO handle multiple notifications
        compilerManager.compile(scope, compileNotification);
      }
    }
  }


  private static CompileScope createScope(Project project,
                                          CompilerManager compilerManager,
                                          ProjectTaskContext context,
                                          Collection<Module> modules) {
    CompileScope scope = compilerManager.createModuleGroupCompileScope(project, modules.toArray(new Module[modules.size()]), true);
    RunConfiguration configuration = context.getRunConfiguration();
    if (configuration != null) {
      scope.putUserData(CompilerManager.RUN_CONFIGURATION_KEY, configuration);
      scope.putUserData(CompilerManager.RUN_CONFIGURATION_TYPE_ID_KEY, configuration.getType().getId());
    }
    ExecutionManagerImpl.EXECUTION_SESSION_ID_KEY.set(scope, context.getSessionId());
    return scope;
  }

  private static void runFilesBuildTasks(@NotNull Project project,
                                         @Nullable CompileStatusNotification compileNotification,
                                         @NotNull Map<Class<? extends ProjectTask>, List<ProjectTask>> tasksMap) {
    Collection<? extends ProjectTask> filesTargets = tasksMap.get(ModuleFilesBuildTask.class);
    if (!ContainerUtil.isEmpty(filesTargets)) {
      VirtualFile[] files = filesTargets.stream()
        .flatMap(target -> Stream.of(ModuleFilesBuildTask.class.cast(target).getFiles()))
        .toArray(VirtualFile[]::new);
      CompilerManager.getInstance(project).compile(files, compileNotification);
    }
  }

  private static void runArtifactsBuildTasks(@NotNull Project project,
                                             @NotNull ProjectTaskContext context,
                                             @Nullable CompileStatusNotification compileNotification,
                                             @NotNull Map<Class<? extends ProjectTask>, List<ProjectTask>> tasksMap) {

    Collection<? extends ProjectTask> buildTasks = tasksMap.get(ArtifactBuildTask.class);
    if (!ContainerUtil.isEmpty(buildTasks)) {
      List<Artifact> toMake = new SmartList<>();
      List<Artifact> toCompile = new SmartList<>();
      for (ProjectTask buildProjectTask : buildTasks) {
        ArtifactBuildTask artifactBuildTask = (ArtifactBuildTask)buildProjectTask;

        if (artifactBuildTask.isIncrementalBuild()) {
          toMake.add(artifactBuildTask.getArtifact());
        }
        else {
          toCompile.add(artifactBuildTask.getArtifact());
        }
      }

      buildArtifacts(project, toMake, context.getSessionId(), compileNotification, false);
      buildArtifacts(project, toCompile, context.getSessionId(), compileNotification, true);
    }
  }

  private static void buildArtifacts(@NotNull Project project,
                                     @NotNull List<Artifact> artifacts,
                                     @Nullable Object sessionId,
                                     @Nullable CompileStatusNotification compileNotification,
                                     boolean forceArtifactBuild) {
    if (!artifacts.isEmpty()) {
      final CompileScope scope = ArtifactCompileScope.createArtifactsScope(project, artifacts, forceArtifactBuild);
      ArtifactsWorkspaceSettings.getInstance(project).setArtifactsToBuild(artifacts);
      ExecutionManagerImpl.EXECUTION_SESSION_ID_KEY.set(scope, sessionId);
      //in external build we can set 'rebuild' flag per target type
      CompilerManager.getInstance(project).make(scope, compileNotification);
    }
  }
}
