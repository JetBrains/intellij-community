/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem.service.project;

import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectCoordinate;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.ProjectModelExternalSource;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 * @since 9/11/2015
 */
public interface IdeModifiableModelsProvider extends IdeModelsProvider, UserDataHolder {
  @NotNull
  Module newModule(@NotNull @NonNls String filePath, final String moduleTypeId);

  @NotNull
  Module newModule(@NotNull ModuleData moduleData);

  @NotNull
  ModifiableModuleModel getModifiableModuleModel();

  @NotNull
  ModifiableRootModel getModifiableRootModel(Module module);

  @NotNull
  ModifiableFacetModel getModifiableFacetModel(Module module);

  @NotNull
  LibraryTable.ModifiableModel getModifiableProjectLibrariesModel();

  Library.ModifiableModel getModifiableLibraryModel(Library library);

  @NotNull
  ModifiableArtifactModel getModifiableArtifactModel();

  Library createLibrary(String name);

  Library createLibrary(String name, @Nullable ProjectModelExternalSource externalSource);

  void removeLibrary(Library library);

  ModalityState getModalityStateForQuestionDialogs();

  ArtifactExternalDependenciesImporter getArtifactExternalDependenciesImporter();

  PackagingElementResolvingContext getPackagingElementResolvingContext();

  void commit();

  void dispose();

  void setTestModuleProperties(Module testModule, String productionModuleName);

  @Nullable
  String getProductionModuleName(Module module);

  @ApiStatus.Experimental
  void registerModulePublication(Module module, ProjectCoordinate modulePublication);

  @ApiStatus.Experimental
  @Nullable
  String findModuleByPublication(ProjectCoordinate publicationId);

  @ApiStatus.Experimental
  @Nullable
  ModuleOrderEntry trySubstitute(Module ownerModule, LibraryOrderEntry libraryOrderEntry, ProjectCoordinate publicationId);

  @ApiStatus.Experimental
  boolean isSubstituted(String libraryName);
}
