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
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.util.Consumer;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.intellij.util.containers.ContainerUtil.list;
import static com.intellij.util.containers.ContainerUtil.map;

/**
 * @author Vladislav.Soroka
 * @since 5/11/2016
 */
public class ActivityManagerImpl extends ActivityManager {

  private final ActivityRunner myDefaultActivityRunner = new InternalActivityRunner();

  public ActivityManagerImpl(@NotNull Project project) {
    super(project);
  }

  @Override
  public void build(@NotNull Module[] modules, @Nullable ActivityStatusNotification callback) {
    run(createModulesBuildActivity(true, modules), callback);
  }

  @Override
  public void rebuild(@NotNull Module[] modules, @Nullable ActivityStatusNotification callback) {
    run(createModulesBuildActivity(false, modules), callback);
  }

  @Override
  public void compile(@NotNull VirtualFile[] files, @Nullable ActivityStatusNotification callback) {
    List<ModuleFilesBuildActivity> buildActivities = Arrays.stream(files)
      .collect(Collectors.groupingBy(file -> ProjectFileIndex.SERVICE.getInstance(myProject).getModuleForFile(file, false)))
      .entrySet().stream()
      .map(entry -> new ModuleFilesBuildActivityImpl(entry.getKey(), false, entry.getValue()))
      .collect(Collectors.toList());

    run(new ActivityList(buildActivities), callback);
  }

  @Override
  public void build(@NotNull Artifact[] artifacts, @Nullable ActivityStatusNotification callback) {
    doBuild(artifacts, callback, true);
  }

  @Override
  public void rebuild(@NotNull Artifact[] artifacts, @Nullable ActivityStatusNotification callback) {
    doBuild(artifacts, callback, false);
  }

  @Override
  public void buildAllModules(@Nullable ActivityStatusNotification callback) {
    run(createAllModulesBuildActivity(true, myProject), callback);
  }

  @Override
  public void rebuildAllModules(@Nullable ActivityStatusNotification callback) {
    run(createAllModulesBuildActivity(false, myProject), callback);
  }

  @Override
  public Activity createAllModulesBuildActivity(boolean isIncrementalBuild, Project project) {
    return createModulesBuildActivity(isIncrementalBuild, ModuleManager.getInstance(project).getModules());
  }

  @Override
  public Activity createModulesBuildActivity(boolean isIncrementalBuild, Module... modules) {
    return modules.length == 1
           ? new ModuleBuildActivityImpl(modules[0], isIncrementalBuild)
           : new ActivityList(map(list(modules), module -> new ModuleBuildActivityImpl(module, isIncrementalBuild)));
  }

  @Override
  public Activity createArtifactsBuildActivity(boolean isIncrementalBuild, Artifact... artifacts) {
    return artifacts.length == 1
           ? new ArtifactBuildActivityImpl(artifacts[0], isIncrementalBuild)
           : new ActivityList(map(list(artifacts), artifact -> new ArtifactBuildActivityImpl(artifact, isIncrementalBuild)));
  }

  @Override
  public void run(@NotNull Activity activity, @Nullable ActivityStatusNotification callback) {
    run(new ActivityContext(), activity, callback);
  }

  @Override
  public void run(@NotNull ActivityContext context, @NotNull Activity activity, @Nullable ActivityStatusNotification callback) {
    List<Pair<ActivityRunner, Collection<? extends Activity>>> toRun = new SmartList<>();

    Consumer<Collection<? extends Activity>> activityClassifier = activities -> {
      Map<ActivityRunner, ? extends List<? extends Activity>> toBuild =
        activities.stream().collect(Collectors.groupingBy(_activity -> {
          for (ActivityRunner runner : getActivityRunners()) {
            if (runner.canRun(_activity)) return runner;
          }
          return myDefaultActivityRunner;
        }));
      for (Map.Entry<ActivityRunner, ? extends List<? extends Activity>> entry : toBuild.entrySet()) {
        toRun.add(Pair.create(entry.getKey(), entry.getValue()));
      }
    };
    visitActivities(activity instanceof ActivityList ? (ActivityList)activity : Collections.singleton(activity), activityClassifier);

    AtomicInteger inProgressCounter = new AtomicInteger(toRun.size());
    AtomicInteger errorsCounter = new AtomicInteger();
    AtomicInteger warningsCounter = new AtomicInteger();
    AtomicBoolean abortedFlag = new AtomicBoolean(false);
    ActivityStatusNotification chunkStatusNotification = callback == null ? null : new ActivityStatusNotification() {
      @Override
      public void finished(@NotNull ActivityExecutionResult executionResult) {
        int inProgress = inProgressCounter.decrementAndGet();
        int allErrors = errorsCounter.addAndGet(executionResult.getErrors());
        int allWarnings = warningsCounter.addAndGet(executionResult.getWarnings());
        if (executionResult.isAborted()) {
          abortedFlag.set(true);
        }
        if (inProgress == 0) {
          callback.finished(new ActivityExecutionResult(abortedFlag.get(), allErrors, allWarnings));
        }
      }
    };

    toRun.forEach(pair -> pair.first.run(myProject, context, chunkStatusNotification, pair.second));
  }

  private static void visitActivities(@NotNull Collection<? extends Activity> activities,
                                      @NotNull Consumer<Collection<? extends Activity>> consumer) {
    for (Activity child : activities) {
      Collection<? extends Activity> _activities;
      if (child instanceof AbstractActivity) {
        _activities = ((AbstractActivity)child).getDependsOn();
      }
      else if (child instanceof ActivityList) {
        _activities = (ActivityList)child;
      }
      else {
        _activities = Collections.singleton(child);
      }

      visitActivities(_activities, consumer);
    }
    consumer.consume(activities);
  }

  @NotNull
  private static ActivityRunner[] getActivityRunners() {
    return ActivityRunner.EP_NAME.getExtensions();
  }

  private void doBuild(@NotNull Artifact[] artifacts, @Nullable ActivityStatusNotification callback, boolean isIncrementalBuild) {
    run(createArtifactsBuildActivity(isIncrementalBuild, artifacts), callback);
  }
}
