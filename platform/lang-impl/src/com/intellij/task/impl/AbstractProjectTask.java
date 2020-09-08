// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  @NotNull
  private Collection<ProjectTask> myDependencies;

  public AbstractProjectTask() {
    this(Collections.emptyList());
  }

  public AbstractProjectTask(@NotNull Collection<ProjectTask> dependencies) {
    myDependencies = dependencies;
  }

  @NotNull
  public Collection<ProjectTask> getDependsOn() {
    return myDependencies;
  }

  public void setDependsOn(@NotNull Collection<ProjectTask> dependencies) {
    myDependencies = dependencies;
  }

  @Override
  @Nls
  public String toString() {
    return getPresentableName();
  }
}
