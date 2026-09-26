// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.facet.impl.ui.libraries;

import com.intellij.facet.impl.ui.FacetEditorContextBase;
import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DelegatingLibrariesValidatorContext implements LibrariesValidatorContext {
  private final FacetEditorContext myDelegate;

  public DelegatingLibrariesValidatorContext(final @NotNull FacetEditorContext delegate) {
    myDelegate = delegate;
  }

  @Override
  public @NotNull Module getModule() {
    return myDelegate.getModule();
  }

  @Override
  public LibrariesContainer getLibrariesContainer() {
    return ((FacetEditorContextBase)myDelegate).getContainer();
  }

  @Override
  public @NotNull ModulesProvider getModulesProvider() {
    return myDelegate.getModulesProvider();
  }

  @Override
  public @Nullable ModifiableRootModel getModifiableRootModel() {
    return myDelegate.getModifiableRootModel();
  }

  @Override
  public @NotNull ModuleRootModel getRootModel() {
    return myDelegate.getRootModel();
  }

}
