/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

/**
 * @author nik
 */
public abstract class FacetEditorContextBase extends UserDataHolderBase implements FacetEditorContext {
  private final FacetsProvider myFacetsProvider;
  @Nullable private final FacetEditorContext myParentContext;
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

  public Library[] getLibraries() {
    return LibraryTablesRegistrar.getInstance().getLibraryTable(getProject()).getLibraries();
  }

  @NotNull
  public String getFacetName() {
    return myFacet.getName();
  }

  public VirtualFile[] getLibraryFiles(final Library library, final OrderRootType rootType) {
    return library.getFiles(rootType);
  }

  @Nullable
  public Library findLibrary(@NotNull String name) {
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

  //todo[nik] pull up to open API?
  public UserDataHolder getSharedModuleData() {
    return mySharedModuleData;
  }

  @NotNull
  public abstract ArtifactsStructureConfigurableContext getArtifactsStructureContext();

  @Override
  @Nullable
  public <T> T getUserData(@NotNull final Key<T> key) {
    T t = super.getUserData(key);
    if (t == null && myParentContext != null) {
      t = myParentContext.getUserData(key);
    }
    return t;
  }

  @NotNull
  public FacetsProvider getFacetsProvider() {
    return myFacetsProvider;
  }

  @NotNull
  public ModulesProvider getModulesProvider() {
    return myModulesProvider;
  }

  @NotNull
  public ModuleRootModel getRootModel() {
    return getModifiableRootModel();
  }

  public abstract LibrariesContainer getContainer();

  @NotNull
  public Facet getFacet() {
    return myFacet;
  }

  @Nullable
  public Facet getParentFacet() {
    return myFacet.getUnderlyingFacet();
  }
}
