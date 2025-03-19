// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.facet.impl.ui.libraries;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainerFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LibrariesValidatorContextImpl implements LibrariesValidatorContext {
  private final Module myModule;
  private final LibrariesContainer myLibrariesContainer;

  public LibrariesValidatorContextImpl(final @NotNull Module module) {
    myModule = module;
    myLibrariesContainer = LibrariesContainerFactory.createContainer(module);
  }

  @Override
  public @NotNull ModuleRootModel getRootModel() {
    return ModuleRootManager.getInstance(myModule);
  }

  @Override
  public @Nullable ModifiableRootModel getModifiableRootModel() {
    return null;
  }

  @Override
  public @NotNull ModulesProvider getModulesProvider() {
    return new DefaultModulesProvider(myModule.getProject());
  }

  @Override
  public @NotNull Module getModule() {
    return myModule;
  }

  @Override
  public LibrariesContainer getLibrariesContainer() {
    return myLibrariesContainer;
  }

}
