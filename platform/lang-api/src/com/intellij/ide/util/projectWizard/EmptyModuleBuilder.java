// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.util.projectWizard;

import com.intellij.openapi.module.ModuleType;

public class EmptyModuleBuilder extends ModuleBuilder {
  @Override
  public boolean isOpenProjectSettingsAfter() {
    return true;
  }

  @Override
  public boolean canCreateModule() {
    return false;
  }

  @Override
  public ModuleType getModuleType() {
    return ModuleType.EMPTY;
  }

  @Override
  public String getPresentableName() {
    return "Empty Project";
  }

  @Override
  public String getGroupName() {
    return getPresentableName();
  }

  @Override
  public boolean isTemplateBased() {
    return true;
  }

  @Override
  public String getDescription() {
    return "Empty project without modules. Use it to create free-style module structure.";
  }
}