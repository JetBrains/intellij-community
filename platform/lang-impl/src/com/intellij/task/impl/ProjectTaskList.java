// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.task.impl;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.task.ProjectTask;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;

/**
 * @author Vladislav.Soroka
 */
public final class ProjectTaskList extends SmartList<ProjectTask> implements ProjectTask {
  public ProjectTaskList() {
  }

  public ProjectTaskList(@NotNull Collection<? extends ProjectTask> c) {
    super(c);
  }

  @Override
  public @NotNull @NlsSafe String getPresentableName() {
    return toString();
  }

  public static @NotNull ProjectTaskList asList(ProjectTask... tasks) {
    return new ProjectTaskList(Arrays.asList(tasks));
  }
}
