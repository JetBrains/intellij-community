// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.scratch;

import com.intellij.openapi.module.Module;
import com.intellij.task.ModuleBuildTask;
import com.intellij.task.impl.ModuleBuildTaskImpl;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public final class JavaScratchModuleBuildTask implements ModuleBuildTask {

  private final ModuleBuildTaskImpl delegate;

  public JavaScratchModuleBuildTask(@NotNull ModuleBuildTaskImpl delegate) {
    this.delegate = delegate;
  }

  @Override
  public boolean isIncrementalBuild() {
    return delegate.isIncrementalBuild();
  }

  @Override
  public @NotNull Module getModule() {
    return delegate.getModule();
  }

  @Override
  public boolean isIncludeDependentModules() {
    return delegate.isIncludeDependentModules();
  }

  @Override
  public boolean isIncludeRuntimeDependencies() {
    return delegate.isIncludeRuntimeDependencies();
  }

  @Nls
  @Override
  public @NotNull String getPresentableName() {
    return delegate.getPresentableName();
  }

  @Override
  public String toString() {
    return delegate.toString();
  }
}
