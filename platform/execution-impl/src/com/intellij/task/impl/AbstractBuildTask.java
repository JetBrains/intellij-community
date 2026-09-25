// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.task.impl;

import com.intellij.task.BuildTask;
import com.intellij.task.ProjectTask;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * @author Vladislav.Soroka
 */
@ApiStatus.Internal
public abstract class AbstractBuildTask extends AbstractProjectTask implements BuildTask {
  private final boolean myIsIncrementalBuild;

  public AbstractBuildTask(boolean isIncrementalBuild) {
    this(isIncrementalBuild, Collections.emptyList());
  }

  public AbstractBuildTask(boolean isIncrementalBuild, @NotNull List<ProjectTask> dependencies) {
    super(dependencies);
    myIsIncrementalBuild = isIncrementalBuild;
  }

  @Override
  public boolean isIncrementalBuild() {
    return myIsIncrementalBuild;
  }
}
