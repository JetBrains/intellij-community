// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectWizard;

import com.intellij.ide.util.projectWizard.ModuleNameLocationSettings;
import com.intellij.ide.util.projectWizard.NamePathComponent;
import org.jetbrains.annotations.NotNull;

public class NewProjectNameLocationSettings implements ModuleNameLocationSettings {
  private final NamePathComponent myProjectNamePathComponent;
  private final ModuleNameLocationComponent myModuleNameLocationComponent;

  public NewProjectNameLocationSettings(NamePathComponent projectNamePathComponent,
                                        ModuleNameLocationComponent moduleNameLocationComponent) {
    myProjectNamePathComponent = projectNamePathComponent;
    myModuleNameLocationComponent = moduleNameLocationComponent;
  }

  @Override
  public @NotNull String getModuleName() {
    return myModuleNameLocationComponent.getModuleName();
  }

  @Override
  public void setModuleName(@NotNull String moduleName) {
    myProjectNamePathComponent.getNameComponent().setText(moduleName);
    myModuleNameLocationComponent.setModuleName(moduleName);
  }

  @Override
  public @NotNull String getModuleContentRoot() {
    return myModuleNameLocationComponent.getModuleContentRoot();
  }

  @Override
  public void setModuleContentRoot(@NotNull String path) {
    myProjectNamePathComponent.setPath(path);
    myModuleNameLocationComponent.setModuleContentRoot(path);
  }
}
