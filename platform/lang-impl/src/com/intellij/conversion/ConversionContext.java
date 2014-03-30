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

package com.intellij.conversion;

import com.intellij.openapi.components.StorageScheme;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;

/**
 * @author nik
 */
public interface ConversionContext {
  @NotNull
  File getProjectBaseDir();

  File getProjectFile();

  StorageScheme getStorageScheme();

  File getSettingsBaseDir();

  ProjectSettings getProjectSettings() throws CannotConvertException;

  RunManagerSettings getRunManagerSettings() throws CannotConvertException;

  WorkspaceSettings getWorkspaceSettings() throws CannotConvertException;

  ModuleSettings getModuleSettings(File moduleFile) throws CannotConvertException;

  @Nullable
  ModuleSettings getModuleSettings(@NotNull String moduleName);

  @NotNull
  String collapsePath(@NotNull String path);

  Collection<File> getLibraryClassRoots(@NotNull String name, @NotNull String level);

  @Nullable
  ComponentManagerSettings getCompilerSettings();

  @Nullable
  ComponentManagerSettings getProjectRootManagerSettings();

  File[] getModuleFiles();

  ComponentManagerSettings getModulesSettings();

  ProjectLibrariesSettings getProjectLibrariesSettings() throws CannotConvertException;

  ArtifactsSettings getArtifactsSettings() throws CannotConvertException;

  @NotNull
  String expandPath(@NotNull String path);
}
