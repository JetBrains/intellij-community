// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.serialization.impl;

import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.service.JpsServiceManager;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * Provides an implementation which takes data from the workspace model instead of reading its directly from the configuration files.
 * Currently, it's disabled by default.
 */
public interface JpsSerializationViaWorkspaceModel {
  boolean IS_ENABLED = SystemProperties.getBooleanProperty("intellij.jps.use.workspace.model", false);
  
  static @Nullable JpsSerializationViaWorkspaceModel getInstance() {
    if (IS_ENABLED) {
      return JpsServiceManager.getInstance().getService(JpsSerializationViaWorkspaceModel.class);
    }
    return null;
  }

  @NotNull JpsModel loadModel(@NotNull Path projectPath,
                              @Nullable Path workspaceStorageCachePath,
                              @Nullable Path externalConfigurationDirectory,
                              @Nullable Path optionsPath,
                              @Nullable Path globalWorkspaceStoragePath,
                              boolean loadUnloadedModules) throws IOException;

  @NotNull JpsProject loadProject(@NotNull Path projectPath, @Nullable Path externalConfigurationDirectory, @NotNull Map<String, String> pathVariables,
                                  boolean loadUnloadedModules) throws IOException;
}
