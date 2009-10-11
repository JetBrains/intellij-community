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

package com.intellij.facet.ui;

import com.intellij.facet.Facet;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.WizardContext;
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
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public interface FacetEditorContext extends UserDataHolder {

  @NotNull
  Project getProject();

  @Nullable
  Library findLibrary(@NotNull String name);

  @Deprecated
  @Nullable
  ModuleBuilder getModuleBuilder();

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

  @Deprecated
  @Nullable
  WizardContext getWizardContext();

  Library createProjectLibrary(@NonNls String name, final VirtualFile[] roots, final VirtualFile[] sources);

  VirtualFile[] getLibraryFiles(Library library, OrderRootType rootType);

  @NotNull
  String getFacetName();
}
