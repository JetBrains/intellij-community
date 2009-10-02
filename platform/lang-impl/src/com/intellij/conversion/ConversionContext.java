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

  @NotNull
  String collapsePath(@NotNull String path);

  Collection<File> getLibraryClassRoots(@NotNull String name, @NotNull String level);

  @Nullable
  ComponentManagerSettings getCompilerSettings();

  @Nullable 
  ComponentManagerSettings getProjectRootManagerSettings();
}
