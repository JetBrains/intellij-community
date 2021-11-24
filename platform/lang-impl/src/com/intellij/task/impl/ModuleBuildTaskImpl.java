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

import com.intellij.lang.LangBundle;
import com.intellij.openapi.module.Module;
import com.intellij.task.ModuleBuildTask;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vladislav.Soroka
 */
public class ModuleBuildTaskImpl extends AbstractBuildTask implements ModuleBuildTask {
  @NotNull
  private final Module myModule;
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

  @NotNull
  @Override
  public Module getModule() {
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

  @NotNull
  @Override
  public String getPresentableName() {
    return LangBundle.message("project.task.name.module.0.build.task", myModule.getName());
  }
}
