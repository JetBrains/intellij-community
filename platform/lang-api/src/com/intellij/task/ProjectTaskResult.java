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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ProjectModelBuildableElement;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * @author Vladislav.Soroka
 */
public class ProjectTaskResult {
  private final boolean myAborted;
  private final int myErrors;
  private final int myWarnings;
  private final Map<ProjectTask, ProjectTaskState> myTasksState;

  public ProjectTaskResult(boolean aborted, int errors, int warnings) {
    myAborted = aborted;
    myErrors = errors;
    myWarnings = warnings;
    myTasksState = Collections.emptyMap();
  }

  public ProjectTaskResult(boolean aborted,
                           int errors,
                           int warnings,
                           @NotNull Map<ProjectTask, ProjectTaskState> tasksState) {
    myAborted = aborted;
    myErrors = errors;
    myWarnings = warnings;
    myTasksState = ContainerUtil.unmodifiableOrEmptyMap(tasksState);
  }

  public boolean isAborted() {
    return myAborted;
  }

  public int getErrors() {
    return myErrors;
  }

  public int getWarnings() {
    return myWarnings;
  }

  @NotNull
  public Map<ProjectTask, ProjectTaskState> getTasksState() {
    return myTasksState;
  }

  public boolean anyMatch(@NotNull BiPredicate<ProjectTask, ProjectTaskState> predicate) {
    return myTasksState.entrySet().stream().anyMatch(entry -> predicate.test(entry.getKey(), entry.getValue()));
  }

  @NotNull
  public List<ProjectTask> getTasks(@NotNull BiPredicate<ProjectTask, ProjectTaskState> predicate) {
    return myTasksState.entrySet().stream()
      .filter(entry -> predicate.test(entry.getKey(), entry.getValue()))
      .map(Map.Entry::getKey)
      .collect(Collectors.toList());
  }

  @NotNull
  public List<Module> getAffectedModules(@NotNull Predicate<ProjectTaskState> predicate) {
    return myTasksState.entrySet().stream()
      .filter(entry -> entry.getKey() instanceof ModuleBuildTask)
      .filter(entry -> predicate.test(entry.getValue()))
      .map(entry -> ((ModuleBuildTask)entry.getKey()).getModule())
      .collect(Collectors.toList());
  }

  @NotNull
  public <T extends ProjectModelBuildableElement> List<T> getBuildableElements(@NotNull Class<T> buildableClass,
                                                                               @NotNull Predicate<ProjectTaskState> predicate) {
    return myTasksState.entrySet().stream()
      .filter(entry -> entry.getKey() instanceof ProjectModelBuildTask<?>)
      .filter(entry -> buildableClass.isInstance(((ProjectModelBuildTask)entry.getKey()).getBuildableElement()))
      .filter(entry -> predicate.test(entry.getValue()))
      .map(entry -> buildableClass.cast(((ProjectModelBuildTask)entry.getKey()).getBuildableElement()))
      .collect(Collectors.toList());
  }
}
