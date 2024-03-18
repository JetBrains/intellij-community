// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module;

import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.pom.Navigatable;

public final class OrderEntryNavigatable implements Navigatable {
  private final Module myModule;
  private final OrderEntry myOrderEntry;

  public OrderEntryNavigatable(Module module, OrderEntry orderEntry) {
    myModule = module;
    myOrderEntry = orderEntry;
  }

  @Override
  public void navigate(boolean requestFocus) {
    ProjectSettingsService.getInstance(myModule.getProject()).openModuleDependenciesSettings(myModule, myOrderEntry);
  }

  @Override
  public boolean canNavigate() {
    return ProjectSettingsService.getInstance(myModule.getProject()).canOpenModuleDependenciesSettings();
  }
}
