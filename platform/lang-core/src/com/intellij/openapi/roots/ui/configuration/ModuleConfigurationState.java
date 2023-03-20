// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.util.UserDataHolder;
import org.jetbrains.annotations.NotNull;

public interface ModuleConfigurationState extends UserDataHolder {
  ModulesProvider getModulesProvider();
  FacetsProvider getFacetsProvider();

  /**
   * Returns an instance which can be used to modify the module configuration
   */
  ModifiableRootModel getModifiableRootModel();

  /**
   * Returns the actual state of the module configuration
   */
  ModuleRootModel getCurrentRootModel();

  @NotNull
  Project getProject();

  /**
   * @deprecated use {@link #getModifiableRootModel()}} if you need to modify the model and use {@link #getCurrentRootModel()} if you just
   * need to read the current state
   */
  @Deprecated(forRemoval = true)
  default ModifiableRootModel getRootModel() {
    return getModifiableRootModel();
  }
}
