// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.task.impl;

import com.intellij.lang.LangBundle;
import com.intellij.task.EmptyCompileScopeBuildTask;
import org.jetbrains.annotations.NotNull;

/**
 * This is task is the opposite to {@link com.intellij.task.ProjectModelBuildTask}.
 * The task can be used to invoke 'empty' compilation that will by default trigger all configured before- and after- compilation tasks.
 * The interpretation of the 'isIncremental' flag is up to the runner that will actually execute this task.
 */
public final class EmptyCompileScopeBuildTaskImpl extends AbstractBuildTask implements EmptyCompileScopeBuildTask {

  public EmptyCompileScopeBuildTaskImpl(boolean isIncrementalBuild) {
    super(isIncrementalBuild);
  }

  @Override
  public @NotNull String getPresentableName() {
    return LangBundle.message("project.task.name.empty.compilation.scope.build.task");
  }
}
