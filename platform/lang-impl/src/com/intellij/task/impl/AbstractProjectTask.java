// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.task.impl;

import com.intellij.task.ProjectTask;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

/**
 * @author Vladislav.Soroka
 */
public abstract class AbstractProjectTask implements ProjectTask {
  private @NotNull Collection<ProjectTask> myDependencies;

  public AbstractProjectTask() {
    this(Collections.emptyList());
  }

  public AbstractProjectTask(@NotNull Collection<ProjectTask> dependencies) {
    myDependencies = dependencies;
  }

  public @NotNull Collection<ProjectTask> getDependsOn() {
    return myDependencies;
  }

  public void setDependsOn(@NotNull Collection<ProjectTask> dependencies) {
    myDependencies = dependencies;
  }

  @Override
  public @Nls String toString() {
    return getPresentableName();
  }
}
