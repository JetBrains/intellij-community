// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.conversion;

import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

public interface ModuleSettings extends ComponentManagerSettings {
  @NonNls String MODULE_ROOT_MANAGER_COMPONENT = "NewModuleRootManager";

  @NotNull
  String getModuleName();

  @Nullable
  String getModuleType();

  @NotNull Path getModuleFile();

  @NotNull
  Collection<Element> getFacetElements(@NotNull String facetTypeId);

  @Nullable
  Element getFacetElement(@NotNull String facetTypeId);

  void setModuleType(@NotNull String moduleType);

  @NotNull
  String expandPath(@NotNull String path);

  @NotNull
  String collapsePath(@NotNull String path);

  @NotNull
  Collection<File> getSourceRoots(boolean includeTests);

  @NotNull
  Collection<File> getContentRoots();

  String getProjectOutputUrl();

  void addExcludedFolder(@NotNull File directory);

  @NotNull
  List<File> getModuleLibraryRoots(String libraryName);

  @NotNull
  Collection<ModuleSettings> getAllModuleDependencies();

  boolean hasModuleLibrary(String libraryName);

  List<Element> getOrderEntries();

  void addFacetElement(@NotNull String facetTypeId, @NotNull String facetName, Element configuration);
}
