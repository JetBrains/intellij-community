// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.facet.ui;

import com.intellij.facet.Facet;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.FacetsProvider;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.NonExtendable
public interface FacetEditorContext extends UserDataHolder {

  @NotNull
  Project getProject();

  @Nullable
  Library findLibrary(@NotNull String name);

  boolean isNewFacet();

  @NotNull
  Facet getFacet();

  @NotNull
  Module getModule();

  @Nullable
  Facet getParentFacet();

  @NotNull
  FacetsProvider getFacetsProvider();

  @NotNull
  ModulesProvider getModulesProvider();

  @NotNull
  ModifiableRootModel getModifiableRootModel();

  @NotNull
  ModuleRootModel getRootModel();

  Library[] getLibraries();

  Library createProjectLibrary(@NonNls String name, final VirtualFile[] roots, final VirtualFile[] sources);

  VirtualFile[] getLibraryFiles(Library library, OrderRootType rootType);

  @NotNull
  String getFacetName();
}
