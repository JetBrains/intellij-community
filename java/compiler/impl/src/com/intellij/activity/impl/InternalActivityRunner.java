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
package com.intellij.activity.impl;

import com.intellij.activity.*;
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
public class InternalActivityRunner extends ActivityRunner {
  @Override
  public void run(@NotNull Project project,
                  @NotNull ActivityContext context,
                  @Nullable ActivityStatusNotification callback,
                  @NotNull Collection<? extends Activity> activities) {
    CompileStatusNotification compileNotification =
      callback == null ? null : (aborted, errors, warnings, compileContext) ->
        callback.finished(new ActivityExecutionResult(aborted, errors, warnings));

    Map<Class<? extends Activity>, List<Activity>> activityMap = groupBy(activities);
    runModulesBuildActivities(project, context, compileNotification, activityMap);
    runFilesBuildActivities(project, compileNotification, activityMap);
    runArtifactsBuildActivities(project, context, compileNotification, activityMap);
  }

  @Override
  public boolean canRun(@NotNull Activity activity) {
    return true;
  }

  @Override
  public ExecutionEnvironment createActivityExecutionEnvironment(@NotNull Project project, @NotNull RunActivity activity) {
    return null;
  }

  public static Map<Class<? extends Activity>, List<Activity>> groupBy(@NotNull Collection<? extends Activity> activities) {
    return activities.stream().collect(Collectors.groupingBy(o -> {
      if (o instanceof ModuleFilesBuildActivity) return ModuleFilesBuildActivity.class;
      if (o instanceof ModuleBuildActivity) return ModuleBuildActivity.class;
      if (o instanceof ArtifactBuildActivity) return ArtifactBuildActivity.class;
      return o.getClass();
    }));
  }

  private static void runModulesBuildActivities(@NotNull Project project,
                                                @NotNull ActivityContext context,
                                                @Nullable CompileStatusNotification compileNotification,
                                                @NotNull Map<Class<? extends Activity>, List<Activity>> activitiesMap) {
    Collection<? extends Activity> buildActivities = activitiesMap.get(ModuleBuildActivity.class);


    if (!ContainerUtil.isEmpty(buildActivities)) {
      List<Module> toMake = new SmartList<>();
      List<Module> toCompile = new SmartList<>();

      for (Activity buildActivity : buildActivities) {
        ModuleBuildActivity moduleBuildActivity = (ModuleBuildActivity)buildActivity;

        if (moduleBuildActivity.isIncrementalBuild()) {
          toMake.add(moduleBuildActivity.getModule());
        }
        else {
          toCompile.add(moduleBuildActivity.getModule());
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
                                          ActivityContext context,
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

  private static void runFilesBuildActivities(@NotNull Project project,
                                              @Nullable CompileStatusNotification compileNotification,
                                              @NotNull Map<Class<? extends Activity>, List<Activity>> activitiesMap) {
    Collection<? extends Activity> filesTargets = activitiesMap.get(ModuleFilesBuildActivity.class);
    if (!ContainerUtil.isEmpty(filesTargets)) {
      VirtualFile[] files = filesTargets.stream()
        .flatMap(target -> Stream.of(ModuleFilesBuildActivity.class.cast(target).getFiles()))
        .toArray(VirtualFile[]::new);
      CompilerManager.getInstance(project).compile(files, compileNotification);
    }
  }

  private static void runArtifactsBuildActivities(@NotNull Project project,
                                                  @NotNull ActivityContext context,
                                                  @Nullable CompileStatusNotification compileNotification,
                                                  @NotNull Map<Class<? extends Activity>, List<Activity>> activitiesMap) {

    Collection<? extends Activity> buildActivities = activitiesMap.get(ArtifactBuildActivity.class);
    if (!ContainerUtil.isEmpty(buildActivities)) {
      List<Artifact> toMake = new SmartList<>();
      List<Artifact> toCompile = new SmartList<>();
      for (Activity buildActivity : buildActivities) {
        ArtifactBuildActivity artifactBuildActivity = (ArtifactBuildActivity)buildActivity;

        if (artifactBuildActivity.isIncrementalBuild()) {
          toMake.add(artifactBuildActivity.getArtifact());
        }
        else {
          toCompile.add(artifactBuildActivity.getArtifact());
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
