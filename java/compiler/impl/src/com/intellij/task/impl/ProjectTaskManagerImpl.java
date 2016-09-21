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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.task.*;
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
public class ProjectTaskManagerImpl extends ProjectTaskManager {

  private final ProjectTaskRunner myDefaultProjectTaskRunner = new InternalProjectTaskRunner();

  public ProjectTaskManagerImpl(@NotNull Project project) {
    super(project);
  }

  @Override
  public void build(@NotNull Module[] modules, @Nullable ProjectTaskNotification callback) {
    run(createModulesBuildTask(true, modules), callback);
  }

  @Override
  public void rebuild(@NotNull Module[] modules, @Nullable ProjectTaskNotification callback) {
    run(createModulesBuildTask(false, modules), callback);
  }

  @Override
  public void compile(@NotNull VirtualFile[] files, @Nullable ProjectTaskNotification callback) {
    List<ModuleFilesBuildTask> buildTasks = Arrays.stream(files)
      .collect(Collectors.groupingBy(file -> ProjectFileIndex.SERVICE.getInstance(myProject).getModuleForFile(file, false)))
      .entrySet().stream()
      .map(entry -> new ModuleFilesBuildTaskImpl(entry.getKey(), false, entry.getValue()))
      .collect(Collectors.toList());

    run(new ProjectTaskList(buildTasks), callback);
  }

  @Override
  public void build(@NotNull Artifact[] artifacts, @Nullable ProjectTaskNotification callback) {
    doBuild(artifacts, callback, true);
  }

  @Override
  public void rebuild(@NotNull Artifact[] artifacts, @Nullable ProjectTaskNotification callback) {
    doBuild(artifacts, callback, false);
  }

  @Override
  public void buildAllModules(@Nullable ProjectTaskNotification callback) {
    run(createAllModulesBuildTask(true, myProject), callback);
  }

  @Override
  public void rebuildAllModules(@Nullable ProjectTaskNotification callback) {
    run(createAllModulesBuildTask(false, myProject), callback);
  }

  @Override
  public ProjectTask createAllModulesBuildTask(boolean isIncrementalBuild, Project project) {
    return createModulesBuildTask(isIncrementalBuild, ModuleManager.getInstance(project).getModules());
  }

  @Override
  public ProjectTask createModulesBuildTask(boolean isIncrementalBuild, Module... modules) {
    return modules.length == 1
           ? new ModuleBuildTaskImpl(modules[0], isIncrementalBuild)
           : new ProjectTaskList(map(list(modules), module -> new ModuleBuildTaskImpl(module, isIncrementalBuild)));
  }

  @Override
  public ProjectTask createArtifactsBuildTask(boolean isIncrementalBuild, Artifact... artifacts) {
    return artifacts.length == 1
           ? new ArtifactBuildTaskImpl(artifacts[0], isIncrementalBuild)
           : new ProjectTaskList(map(list(artifacts), artifact -> new ArtifactBuildTaskImpl(artifact, isIncrementalBuild)));
  }

  @Override
  public void run(@NotNull ProjectTask projectTask, @Nullable ProjectTaskNotification callback) {
    run(new ProjectTaskContext(), projectTask, callback);
  }

  @Override
  public void run(@NotNull ProjectTaskContext context, @NotNull ProjectTask projectTask, @Nullable ProjectTaskNotification callback) {
    List<Pair<ProjectTaskRunner, Collection<? extends ProjectTask>>> toRun = new SmartList<>();

    Consumer<Collection<? extends ProjectTask>> taskClassifier = tasks -> {
      Map<ProjectTaskRunner, ? extends List<? extends ProjectTask>> toBuild =
        tasks.stream().collect(Collectors.groupingBy(aTask -> {
          for (ProjectTaskRunner runner : getTaskRunners()) {
            if (runner.canRun(aTask)) return runner;
          }
          return myDefaultProjectTaskRunner;
        }));
      for (Map.Entry<ProjectTaskRunner, ? extends List<? extends ProjectTask>> entry : toBuild.entrySet()) {
        toRun.add(Pair.create(entry.getKey(), entry.getValue()));
      }
    };
    visitTasks(projectTask instanceof ProjectTaskList ? (ProjectTaskList)projectTask : Collections.singleton(projectTask), taskClassifier);

    AtomicInteger inProgressCounter = new AtomicInteger(toRun.size());
    AtomicInteger errorsCounter = new AtomicInteger();
    AtomicInteger warningsCounter = new AtomicInteger();
    AtomicBoolean abortedFlag = new AtomicBoolean(false);
    ProjectTaskNotification chunkStatusNotification = callback == null ? null : new ProjectTaskNotification() {
      @Override
      public void finished(@NotNull ProjectTaskResult executionResult) {
        int inProgress = inProgressCounter.decrementAndGet();
        int allErrors = errorsCounter.addAndGet(executionResult.getErrors());
        int allWarnings = warningsCounter.addAndGet(executionResult.getWarnings());
        if (executionResult.isAborted()) {
          abortedFlag.set(true);
        }
        if (inProgress == 0) {
          callback.finished(new ProjectTaskResult(abortedFlag.get(), allErrors, allWarnings));
        }
      }
    };

    toRun.forEach(pair -> pair.first.run(myProject, context, chunkStatusNotification, pair.second));
  }

  private static void visitTasks(@NotNull Collection<? extends ProjectTask> tasks,
                                 @NotNull Consumer<Collection<? extends ProjectTask>> consumer) {
    for (ProjectTask child : tasks) {
      Collection<? extends ProjectTask> taskDependencies;
      if (child instanceof AbstractProjectTask) {
        taskDependencies = ((AbstractProjectTask)child).getDependsOn();
      }
      else if (child instanceof ProjectTaskList) {
        taskDependencies = (ProjectTaskList)child;
      }
      else {
        taskDependencies = Collections.singleton(child);
      }

      visitTasks(taskDependencies, consumer);
    }
    consumer.consume(tasks);
  }

  @NotNull
  private static ProjectTaskRunner[] getTaskRunners() {
    return ProjectTaskRunner.EP_NAME.getExtensions();
  }

  private void doBuild(@NotNull Artifact[] artifacts, @Nullable ProjectTaskNotification callback, boolean isIncrementalBuild) {
    run(createArtifactsBuildTask(isIncrementalBuild, artifacts), callback);
  }
}
