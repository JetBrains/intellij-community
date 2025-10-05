// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.serialization;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsGlobal;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.serialization.impl.JpsModuleSerializationDataExtensionImpl;
import org.jetbrains.jps.model.serialization.impl.JpsPathVariablesConfigurationImpl;
import org.jetbrains.jps.model.serialization.impl.JpsProjectSerializationDataExtensionImpl;
import org.jetbrains.jps.model.serialization.module.JpsModuleSerializationDataExtension;
import org.jetbrains.jps.service.JpsServiceManager;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class JpsModelSerializationDataService {
  private JpsModelSerializationDataService() {
  }

  public static Map<String, String> computeAllPathVariables(JpsGlobal global) {
    Map<String, String> pathVariables = new HashMap<>(PathMacroUtil.getGlobalSystemMacros(false));
    JpsPathVariablesConfiguration configuration = getPathVariablesConfiguration(global);
    if (configuration != null) {
      pathVariables.putAll(configuration.getAllUserVariables());
    }
    for (JpsPathMacroContributor extension : JpsServiceManager.getInstance().getExtensions(JpsPathMacroContributor.class)) {
      pathVariables.putAll(extension.getPathMacros());
    }
    return pathVariables;
  }

  @ApiStatus.Internal
  public static @Nullable JpsPathVariablesConfiguration getPathVariablesConfiguration(JpsGlobal global) {
    return global.getContainer().getChild(JpsGlobalLoader.PATH_VARIABLES_ROLE);
  }

  @ApiStatus.Internal
  public static @NotNull JpsPathVariablesConfiguration getOrCreatePathVariablesConfiguration(JpsGlobal global) {
    JpsPathVariablesConfiguration child = global.getContainer().getChild(JpsGlobalLoader.PATH_VARIABLES_ROLE);
    if (child == null) {
      return global.getContainer().setChild(JpsGlobalLoader.PATH_VARIABLES_ROLE, new JpsPathVariablesConfigurationImpl());
    }
    return child;
  }

  @ApiStatus.Internal
  public static @Nullable JpsProjectSerializationDataExtension getProjectExtension(@NotNull JpsProject project) {
    return project.getContainer().getChild(JpsProjectSerializationDataExtensionImpl.ROLE);
  }

  public static @Nullable Path getBaseDirectoryPath(@NotNull JpsProject project) {
    JpsProjectSerializationDataExtension extension = getProjectExtension(project);
    return extension == null ? null : extension.getBaseDirectoryPath();
  }

  /**
   * Use {@link #getBaseDirectoryPath(JpsProject)} instead
   */
  @ApiStatus.Obsolete
  public static @Nullable File getBaseDirectory(@NotNull JpsProject project) {
    JpsProjectSerializationDataExtension extension = getProjectExtension(project);
    return extension == null ? null : extension.getBaseDirectory();
  }

  @ApiStatus.Internal
  public static @Nullable JpsModuleSerializationDataExtension getModuleExtension(@NotNull JpsModule project) {
    return project.getContainer().getChild(JpsModuleSerializationDataExtensionImpl.ROLE);
  }

  public static @Nullable Path getBaseDirectoryPath(@NotNull JpsModule module) {
    JpsModuleSerializationDataExtension extension = getModuleExtension(module);
    return extension == null ? null : extension.getBaseDirectoryPath();
  }
  
  /**
   * Use {@link #getBaseDirectoryPath(JpsModule)} instead
   */
  @ApiStatus.Obsolete
  public static @Nullable File getBaseDirectory(@NotNull JpsModule module) {
    JpsModuleSerializationDataExtension extension = getModuleExtension(module);
    return extension == null ? null : extension.getBaseDirectory();
  }

  public static @Nullable String getPathVariableValue(@NotNull JpsGlobal global, @NotNull String name) {
    String value = PathMacroUtil.getGlobalSystemMacroValue(name, false);
    if (value != null) {
      return value;
    }
    JpsPathVariablesConfiguration configuration = getPathVariablesConfiguration(global);
    return configuration != null ? configuration.getUserVariableValue(name) : null;
  }
}
