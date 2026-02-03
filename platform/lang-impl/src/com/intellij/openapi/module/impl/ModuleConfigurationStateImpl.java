// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module.impl;

import com.intellij.facet.impl.DefaultFacetsProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.ui.configuration.FacetsProvider;
import com.intellij.openapi.roots.ui.configuration.ModuleConfigurationState;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.UserDataHolderBase;
import org.jetbrains.annotations.NotNull;

public class ModuleConfigurationStateImpl extends UserDataHolderBase implements ModuleConfigurationState {
  private final ModulesProvider myProvider;
  private final Project myProject;

  public ModuleConfigurationStateImpl(@NotNull Project project, @NotNull ModulesProvider provider) {
    myProvider = provider;
    myProject = project;
  }

  @Override
  public ModulesProvider getModulesProvider() {
    return myProvider;
  }

  @Override
  public FacetsProvider getFacetsProvider() {
    return DefaultFacetsProvider.INSTANCE;
  }

  @Override
  public ModifiableRootModel getModifiableRootModel() {
    return null;
  }

  @Override
  public ModuleRootModel getCurrentRootModel() {
    return null;
  }

  @Override
  public @NotNull Project getProject() {
    return myProject;
  }
}
