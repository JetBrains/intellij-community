package com.intellij.openapi.module.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ui.configuration.FacetsProvider;
import com.intellij.openapi.roots.ui.configuration.ModuleConfigurationState;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.UserDataHolderBase;

public class ModuleConfigurationStateImpl extends UserDataHolderBase implements ModuleConfigurationState {
  private final ModulesProvider myProvider;
  private final ModifiableRootModel myRootModel;
  private final Project myProject;
  private final FacetsProvider myFacetsProvider;

  public ModuleConfigurationStateImpl(Project project, ModulesProvider provider, ModifiableRootModel rootModel,
                                      final FacetsProvider facetsProvider) {
    myFacetsProvider = facetsProvider;
    myProvider = provider;
    myRootModel = rootModel;
    myProject = project;
  }

  public ModulesProvider getModulesProvider() {
    return myProvider;
  }

  public FacetsProvider getFacetsProvider() {
    return myFacetsProvider;
  }

  public ModifiableRootModel getRootModel() {
    return myRootModel;
  }

  public Project getProject() {
    return myProject;
  }
}
