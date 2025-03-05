// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.facet.impl.ui;

import com.intellij.facet.Facet;
import com.intellij.facet.impl.DefaultFacetsProvider;
import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.ui.configuration.FacetsProvider;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactsStructureConfigurableContext;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class FacetEditorContextBase extends UserDataHolderBase implements FacetEditorContext {
  private final FacetsProvider myFacetsProvider;
  private final @Nullable FacetEditorContext myParentContext;
  private final ModulesProvider myModulesProvider;
  private final Facet myFacet;
  private final UserDataHolder mySharedModuleData;
  private final UserDataHolder mySharedProjectData;

  public FacetEditorContextBase(@NotNull Facet facet, final @Nullable FacetEditorContext parentContext, final @Nullable FacetsProvider facetsProvider,
                                final @NotNull ModulesProvider modulesProvider,
                                final UserDataHolder sharedModuleData,
                                final UserDataHolder sharedProjectData) {
    myFacet = facet;
    mySharedProjectData = sharedProjectData;
    mySharedModuleData = sharedModuleData;
    myParentContext = parentContext;
    myModulesProvider = modulesProvider;
    myFacetsProvider = facetsProvider != null ? facetsProvider : DefaultFacetsProvider.INSTANCE;
  }

  @Override
  public Library[] getLibraries() {
    return LibraryTablesRegistrar.getInstance().getLibraryTable(getProject()).getLibraries();
  }

  @Override
  public @NotNull String getFacetName() {
    return myFacet.getName();
  }

  @Override
  public VirtualFile[] getLibraryFiles(final Library library, final OrderRootType rootType) {
    return library.getFiles(rootType);
  }

  @Override
  public @Nullable Library findLibrary(@NotNull String name) {
    for (Library library : getLibraries()) {
      if (name.equals(library.getName())) {
        return library;
      }
    }
    return null;
  }


  public UserDataHolder getSharedProjectData() {
    return mySharedProjectData;
  }

  public UserDataHolder getSharedModuleData() {
    return mySharedModuleData;
  }

  public abstract @NotNull ArtifactsStructureConfigurableContext getArtifactsStructureContext();

  @Override
  public @Nullable <T> T getUserData(final @NotNull Key<T> key) {
    T t = super.getUserData(key);
    if (t == null && myParentContext != null) {
      t = myParentContext.getUserData(key);
    }
    return t;
  }

  @Override
  public @NotNull FacetsProvider getFacetsProvider() {
    return myFacetsProvider;
  }

  @Override
  public @NotNull ModulesProvider getModulesProvider() {
    return myModulesProvider;
  }

  @Override
  public @NotNull ModuleRootModel getRootModel() {
    return getModifiableRootModel();
  }

  public abstract LibrariesContainer getContainer();

  @Override
  public @NotNull Facet getFacet() {
    return myFacet;
  }

  @Override
  public @Nullable Facet getParentFacet() {
    return myFacet.getUnderlyingFacet();
  }
}
