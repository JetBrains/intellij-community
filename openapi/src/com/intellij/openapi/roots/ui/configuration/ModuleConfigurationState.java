package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.util.UserDataHolder;

public interface ModuleConfigurationState extends UserDataHolder {
  ModulesProvider getModulesProvider();
  ModifiableRootModel getRootModel();
  Project getProject();
}