// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.projectRoot.daemon;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.impl.OrderEntryUtil;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.util.ActionCallback;
import org.jetbrains.annotations.NotNull;

public class PlaceInModuleClasspath extends PlaceInProjectStructure {
  private final StructureConfigurableContext myContext;
  private final Module myModule;
  private final ProjectStructureElement myElement;
  private final OrderEntry myOrderEntry;

  public PlaceInModuleClasspath(StructureConfigurableContext context, Module module, ProjectStructureElement element, OrderEntry orderEntry) {
    myContext = context;
    myModule = module;
    myElement = element;
    myOrderEntry = orderEntry;
  }

  public PlaceInModuleClasspath(@NotNull StructureConfigurableContext context, @NotNull Module module, ProjectStructureElement element, @NotNull ProjectStructureElement elementInClasspath) {
    myContext = context;
    myModule = module;
    myElement = element;
    ModuleRootModel rootModel = myContext.getModulesConfigurator().getRootModel(myModule);
    if (elementInClasspath instanceof LibraryProjectStructureElement) {
      myOrderEntry = OrderEntryUtil.findLibraryOrderEntry(rootModel, ((LibraryProjectStructureElement)elementInClasspath).getLibrary());
    }
    else if (elementInClasspath instanceof ModuleProjectStructureElement) {
      myOrderEntry = OrderEntryUtil.findModuleOrderEntry(rootModel, ((ModuleProjectStructureElement)elementInClasspath).getModule());
    }
    else if (elementInClasspath instanceof SdkProjectStructureElement) {
      myOrderEntry = OrderEntryUtil.findJdkOrderEntry(rootModel, ((SdkProjectStructureElement)elementInClasspath).getSdk());
    }
    else {
      myOrderEntry = null;
    }
  }

  @Override
  public @NotNull ProjectStructureElement getContainingElement() {
    return myElement;
  }

  @Override
  public String getPlacePath() {
    return myOrderEntry != null ? myOrderEntry.getPresentableName() : null;
  }

  @Override
  public @NotNull ActionCallback navigate() {
    return myContext.getModulesConfigurator().getProjectStructureConfigurable().selectOrderEntry(myModule, myOrderEntry);
  }
}
