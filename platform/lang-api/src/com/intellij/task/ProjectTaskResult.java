// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
 * @deprecated in favour of {@link ProjectTaskManager.Result}
 */
@Deprecated(forRemoval = true)
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

  public @NotNull Map<ProjectTask, ProjectTaskState> getTasksState() {
    return myTasksState;
  }

  public boolean anyMatch(@NotNull BiPredicate<? super ProjectTask, ? super ProjectTaskState> predicate) {
    return myTasksState.entrySet().stream().anyMatch(entry -> predicate.test(entry.getKey(), entry.getValue()));
  }

  public @NotNull List<ProjectTask> getTasks(@NotNull BiPredicate<? super ProjectTask, ? super ProjectTaskState> predicate) {
    return myTasksState.entrySet().stream()
      .filter(entry -> predicate.test(entry.getKey(), entry.getValue()))
      .map(Map.Entry::getKey)
      .collect(Collectors.toList());
  }

  public @NotNull List<Module> getAffectedModules(@NotNull Predicate<? super ProjectTaskState> predicate) {
    return myTasksState.entrySet().stream()
      .filter(entry -> entry.getKey() instanceof ModuleBuildTask)
      .filter(entry -> predicate.test(entry.getValue()))
      .map(entry -> ((ModuleBuildTask)entry.getKey()).getModule())
      .collect(Collectors.toList());
  }

  public @NotNull <T extends ProjectModelBuildableElement> List<T> getBuildableElements(@NotNull Class<? extends T> buildableClass,
                                                                                        @NotNull Predicate<? super ProjectTaskState> predicate) {
    return myTasksState.entrySet().stream()
      .filter(entry -> entry.getKey() instanceof ProjectModelBuildTask<?>)
      .filter(entry -> buildableClass.isInstance(((ProjectModelBuildTask<?>)entry.getKey()).getBuildableElement()))
      .filter(entry -> predicate.test(entry.getValue()))
      .map(entry -> buildableClass.cast(((ProjectModelBuildTask<?>)entry.getKey()).getBuildableElement()))
      .collect(Collectors.toList());
  }
}
