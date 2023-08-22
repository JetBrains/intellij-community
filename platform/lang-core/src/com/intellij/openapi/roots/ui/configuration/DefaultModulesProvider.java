// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.facet.FacetModel;
import com.intellij.facet.FacetManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DefaultModulesProvider implements ModulesProvider {

  @NotNull
  public static ModulesProvider createForProject(@Nullable Project project) {
    return project == null ? EMPTY_MODULES_PROVIDER : new DefaultModulesProvider(project);
  }

  private final Project myProject;

  public DefaultModulesProvider(final Project project) {
    myProject = project;
  }

  @Override
  public Module @NotNull [] getModules() {
    return ModuleManager.getInstance(myProject).getModules();
  }

  @Override
  public Module getModule(@NotNull String name) {
    return ModuleManager.getInstance(myProject).findModuleByName(name);
  }

  @Override
  public ModuleRootModel getRootModel(@NotNull Module module) {
    return ModuleRootManager.getInstance(module);
  }

  @NotNull
  @Override
  public FacetModel getFacetModel(@NotNull Module module) {
    return FacetManager.getInstance(module);
  }
}
