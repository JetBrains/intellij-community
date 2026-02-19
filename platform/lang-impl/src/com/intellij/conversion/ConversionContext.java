// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.conversion;

import com.intellij.openapi.components.StorageScheme;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;

public interface ConversionContext {
  @NotNull Path getProjectBaseDir();

  /**
   * @return path to parent directory of .idea directory for a directory-based storage scheme or path to ipr-file for a file-based scheme
   */
  @NotNull Path getProjectFile();

  @NotNull
  StorageScheme getStorageScheme();

  /**
   * @return .idea directory for a directory-based storage scheme or {@code null} for file-based scheme
   */
  @Nullable Path getSettingsBaseDir();

  /**
   * @return .ipr file for a file-based storage scheme or {@code null} for directory-based scheme
   */
  @Nullable ComponentManagerSettings getProjectSettings();

  WorkspaceSettings getWorkspaceSettings() throws CannotConvertException;

  ModuleSettings getModuleSettings(@NotNull Path moduleFile) throws CannotConvertException;

  @Nullable
  ModuleSettings getModuleSettings(@NotNull String moduleName);

  /**
   * @param fileName name of the file under .idea directory which contains the settings.
   *                 For ipr-based storage format, the settings will
   *                 be loaded from ipr-file
   * @return {@link ComponentManagerSettings} instance which can be used to read and modify the settings.
   */
  @NotNull ComponentManagerSettings createProjectSettings(@NotNull String fileName);

  @NotNull
  String collapsePath(@NotNull String path);

  @Nullable
  ComponentManagerSettings getCompilerSettings();

  @Nullable
  ComponentManagerSettings getProjectRootManagerSettings();

  @NotNull
  List<Path> getModulePaths() throws CannotConvertException;

  ProjectLibrariesSettings getProjectLibrarySettings() throws CannotConvertException;

  @NotNull
  String expandPath(@NotNull String path);
}
