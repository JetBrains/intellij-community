package com.intellij.conversion;

import com.intellij.openapi.components.StorageScheme;
import org.jetbrains.annotations.NotNull;

import java.io.File;

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
}
