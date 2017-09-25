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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
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
  private static final Logger LOG = Logger.getInstance(InternalProjectTaskRunner.class);
  public static final Key<Object> EXECUTION_SESSION_ID_KEY = ExecutionManagerImpl.EXECUTION_SESSION_ID_KEY;

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
    if (ContainerUtil.isEmpty(buildTasks)) return;
    ModulesBuildSettings modulesBuildSettings = assembleModulesBuildSettings(buildTasks);

    CompilerManager compilerManager = CompilerManager.getInstance(project);
    CompileScope scope = createScope(compilerManager, context,
                                     modulesBuildSettings.modules,
                                     modulesBuildSettings.includeDependentModules,
                                     modulesBuildSettings.includeRuntimeDependencies);
    if (modulesBuildSettings.isIncrementalBuild) {
      compilerManager.make(scope, compileNotification);
    }
    else {
      compilerManager.compile(scope, compileNotification);
    }
  }

  private static class ModulesBuildSettings {
    final boolean isIncrementalBuild;
    final boolean includeDependentModules;
    final boolean includeRuntimeDependencies;
    final Collection<Module> modules;

    public ModulesBuildSettings(boolean isIncrementalBuild,
                                boolean includeDependentModules,
                                boolean includeRuntimeDependencies,
                                Collection<Module> modules) {
      this.isIncrementalBuild = isIncrementalBuild;
      this.includeDependentModules = includeDependentModules;
      this.includeRuntimeDependencies = includeRuntimeDependencies;
      this.modules = modules;
    }
  }

  private static ModulesBuildSettings assembleModulesBuildSettings(Collection<? extends ProjectTask> buildTasks) {
    Collection<Module> modules = new SmartList<>();
    Collection<ModuleBuildTask> incrementalTasks = ContainerUtil.newSmartList();
    Collection<ModuleBuildTask> excludeDependentTasks = ContainerUtil.newSmartList();
    Collection<ModuleBuildTask> excludeRuntimeTasks = ContainerUtil.newSmartList();

    for (ProjectTask buildProjectTask : buildTasks) {
      ModuleBuildTask moduleBuildTask = (ModuleBuildTask)buildProjectTask;
      modules.add(moduleBuildTask.getModule());

      if (moduleBuildTask.isIncrementalBuild()) {
        incrementalTasks.add(moduleBuildTask);
      }
      if (!moduleBuildTask.isIncludeDependentModules()) {
        excludeDependentTasks.add(moduleBuildTask);
      }
      if (!moduleBuildTask.isIncludeRuntimeDependencies()) {
        excludeRuntimeTasks.add(moduleBuildTask);
      }
    }

    boolean isIncrementalBuild = incrementalTasks.size() == buildTasks.size();
    boolean includeDependentModules = excludeDependentTasks.size() != buildTasks.size();
    boolean includeRuntimeDependencies = excludeRuntimeTasks.size() != buildTasks.size();

    if (!isIncrementalBuild && !incrementalTasks.isEmpty()) {
      assertModuleBuildSettingsConsistent(incrementalTasks, "will be built ignoring incremental build setting");
    }
    if (includeDependentModules && !excludeDependentTasks.isEmpty()) {
      assertModuleBuildSettingsConsistent(excludeDependentTasks, "will be built along with dependent modules");
    }
    if (includeRuntimeDependencies && !excludeRuntimeTasks.isEmpty()) {
      assertModuleBuildSettingsConsistent(excludeRuntimeTasks, "will be built along with runtime dependencies");
    }
    return new ModulesBuildSettings(isIncrementalBuild, includeDependentModules, includeRuntimeDependencies, modules);
  }

  private static void assertModuleBuildSettingsConsistent(Collection<ModuleBuildTask> moduleBuildTasks, String warnMsg) {
    String moduleNames = StringUtil.join(moduleBuildTasks, task -> task.getModule().getName(), ", ");
    LOG.warn("Module" + (moduleBuildTasks.size() > 1 ? "s": "") + " : '" + moduleNames + "' " + warnMsg);
  }

  private static CompileScope createScope(CompilerManager compilerManager,
                                          ProjectTaskContext context,
                                          Collection<Module> modules,
                                          boolean includeDependentModules,
                                          boolean includeRuntimeDependencies) {
    CompileScope scope = compilerManager.createModulesCompileScope(
      modules.toArray(new Module[modules.size()]), includeDependentModules, includeRuntimeDependencies);
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
