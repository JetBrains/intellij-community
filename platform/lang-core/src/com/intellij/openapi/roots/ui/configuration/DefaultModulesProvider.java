// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.roots.ui.configuration;

import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DefaultModulesProvider implements ModulesProvider {

  public static @NotNull ModulesProvider createForProject(@Nullable Project project) {
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

  @Override
  public @NotNull FacetModel getFacetModel(@NotNull Module module) {
    return FacetManager.getInstance(module);
  }
}
