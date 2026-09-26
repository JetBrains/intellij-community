// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.task.impl;

import com.intellij.lang.LangBundle;
import com.intellij.openapi.module.Module;
import com.intellij.task.ModuleBuildTask;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vladislav.Soroka
 */
public class ModuleBuildTaskImpl extends AbstractBuildTask implements ModuleBuildTask {
  private final @NotNull Module myModule;
  private final boolean myIncludeDependentModules;
  private final boolean myIncludeRuntimeDependencies;
  private final boolean myIncludeTests;

  public ModuleBuildTaskImpl(@NotNull Module module) {
    this(module, true);
  }

  public ModuleBuildTaskImpl(@NotNull Module module, boolean isIncrementalBuild) {
    this(module, isIncrementalBuild, true, false);
  }

  public ModuleBuildTaskImpl(@NotNull Module module, boolean isIncrementalBuild, boolean includeDependentModules, boolean includeRuntimeDependencies) {
    this(module, isIncrementalBuild, includeDependentModules,includeRuntimeDependencies, true);
  }
  
  public ModuleBuildTaskImpl(@NotNull Module module, boolean isIncrementalBuild, boolean includeDependentModules, boolean includeRuntimeDependencies, boolean includeTests) {
    super(isIncrementalBuild);
    myModule = module;
    myIncludeDependentModules = includeDependentModules;
    myIncludeRuntimeDependencies = includeRuntimeDependencies;
    myIncludeTests = includeTests;
  }

  @Override
  public @NotNull Module getModule() {
    return myModule;
  }

  @Override
  public boolean isIncludeDependentModules() {
    return myIncludeDependentModules;
  }

  @Override
  public boolean isIncludeRuntimeDependencies() {
    return myIncludeRuntimeDependencies;
  }

  @Override
  public boolean isIncludeTests() {
    return myIncludeTests;
  }

  @Override
  public @NotNull String getPresentableName() {
    return LangBundle.message("project.task.name.module.0.build.task", myModule.getName());
  }
}
