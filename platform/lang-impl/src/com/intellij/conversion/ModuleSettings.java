package com.intellij.conversion;

import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.List;

/**
 * @author nik
 */
public interface ModuleSettings extends ComponentManagerSettings {

  @NotNull
  String getModuleName();

  @Nullable
  String getModuleType();

  @NotNull
  File getModuleFile();

  @NotNull
  Collection<? extends Element> getFacetElements(@NotNull String facetTypeId);

  void setModuleType(@NotNull String moduleType);

  @NotNull
  String expandPath(@NotNull String path);

  @NotNull
  Collection<File> getSourceRoots(boolean includeTests);

  @NotNull
  Collection<File> getContentRoots();

  void addExcludedFolder(@NotNull File directory);

  List<File> getModuleLibraryRootUrls(String libraryName);
}
