// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.serialization;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.service.JpsServiceManager;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

public abstract class JpsSerializationManager {
  public static JpsSerializationManager getInstance() {
    return JpsServiceManager.getInstance().getService(JpsSerializationManager.class);
  }

  protected JpsSerializationManager() {
  }

  public @NotNull JpsModel loadModel(@NotNull String projectPath, @Nullable String optionsPath) throws IOException {
    return loadModel(projectPath, optionsPath, false);
  }

  public abstract @NotNull JpsModel loadModel(@NotNull String projectPath, @Nullable String optionsPath, boolean loadUnloadedModules) throws IOException;

  /**
   * Loads project configuration and global options from the given {@code projectPath} and {@code optionsPath}.
   *
   * @param projectPath path to the directory containing .idea or to *.ipr file
   * @param externalConfigurationDirectory path to the directory containing configuration of parts imported from external systems
   * @param optionsPath path to {@code ${idea.config.path}/options} directory 
   */
  public abstract @NotNull JpsModel loadModel(@NotNull Path projectPath, @Nullable Path externalConfigurationDirectory, @Nullable Path optionsPath,
                                     boolean loadUnloadedModules) throws IOException;

  /**
   * Loads project without unloaded modules.
   */
  public abstract @NotNull JpsProject loadProject(@NotNull String projectPath, @NotNull Map<String, String> pathVariables) throws IOException;

  public abstract @NotNull JpsProject loadProject(@NotNull String projectPath, @NotNull Map<String, String> pathVariables,
                                                  boolean loadUnloadedModules) throws IOException;

  /**
   * Loads project configuration from the given {@code projectPath}.
   * @param projectPath path to the directory containing .idea or to *.ipr file
   * @param externalConfigurationDirectory path to the directory containing configuration of parts imported from external systems                   
   */
  public abstract @NotNull JpsProject loadProject(@NotNull Path projectPath,
                                         @Nullable Path externalConfigurationDirectory,
                                         @NotNull Map<String, String> pathVariables,
                                         boolean loadUnloadedModules) throws IOException;
}
