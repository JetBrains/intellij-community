package com.intellij.openapi.externalSystem.service.settings;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Searches external system project configuration files and build scripts.
 */
public interface ExternalSystemConfigLocator {

  ExtensionPointName<ExternalSystemConfigLocator> EP_NAME = ExtensionPointName.create("com.intellij.externalSystemConfigLocator");

  /**
   * External system id is needed to find applicable config locator.
   */
  @NotNull ProjectSystemId getTargetExternalSystemId();

  /**
   * Finds any of external system configuration files which are under {@code configPath}.
   * <p>
   * Example: 'gradle' external system stores config file parent as config path, and we might want to locate exact config file
   * given it's directory file.
   *
   * @param configPath base config file
   * @return config file to use (if any)
   */
  @Nullable
  VirtualFile adjust(@NotNull VirtualFile configPath);

  /**
   * Returns all configuration files used by external system to build the project.
   *
   * @param externalProjectSettings external system project settings
   * @return external system project config files
   */
  @NotNull
  List<VirtualFile> findAll(@NotNull ExternalProjectSettings externalProjectSettings);
}
