// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.serialization.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.serialization.*;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public final class JpsSerializationManagerImpl extends JpsSerializationManager {
  @Override
  public @NotNull JpsModel loadModel(@NotNull String projectPath, @Nullable String optionsPathString, boolean loadUnloadedModules) throws IOException {
    Path optionsPath = optionsPathString == null ? null : Path.of(optionsPathString).normalize();
    Path externalConfigurationDirectory = JpsProjectConfigurationLoading.getExternalConfigurationDirectoryFromSystemProperty();
    return loadModel(Path.of(projectPath), externalConfigurationDirectory, optionsPath, loadUnloadedModules);
  }

  @Override
  public @NotNull JpsModel loadModel(@NotNull Path projectPath,
                                     @Nullable Path externalConfigurationDirectory,
                                     @Nullable Path optionsPath,
                                     boolean loadUnloadedModules) throws IOException {
    JpsSerializationViaWorkspaceModel serializationViaWorkspaceModel = JpsSerializationViaWorkspaceModel.getInstance();
    if (serializationViaWorkspaceModel != null) {
      String projectCachePath = System.getProperty("jps.workspace.storage.project.cache.path");
      Path workspaceStorageCachePath = projectCachePath == null ? null : Path.of(projectCachePath);
      String globalCachePath = System.getProperty("jps.workspace.storage.global.cache.path");
      Path globalWorkspaceStoragePath = globalCachePath == null ? null : Path.of(globalCachePath);
      return serializationViaWorkspaceModel.loadModel(projectPath, workspaceStorageCachePath, externalConfigurationDirectory, optionsPath,
                                                      globalWorkspaceStoragePath, loadUnloadedModules);
    }

    JpsModel model = JpsElementFactory.getInstance().createModel();
    if (optionsPath != null) {
      JpsGlobalSettingsLoading.loadGlobalSettings(model.getGlobal(), optionsPath);
    }
    Map<String, String> pathVariables = JpsModelSerializationDataService.computeAllPathVariables(model.getGlobal());
    JpsProject project = model.getProject();
    JpsPathMapper pathMapper = model.getGlobal().getPathMapper();
    JpsProjectLoader.loadProject(project, pathVariables, pathMapper, projectPath, loadUnloadedModules, externalConfigurationDirectory);
    return model;
  }

  @Override
  public @NotNull JpsProject loadProject(@NotNull String projectPath, @NotNull Map<String, String> pathVariables) throws IOException {
    return loadProject(projectPath, pathVariables, false);
  }

  @Override
  public @NotNull JpsProject loadProject(@NotNull String projectPathString, @NotNull Map<String, String> pathVariables, boolean loadUnloadedModules) throws IOException {
    Path projectPath = Paths.get(projectPathString);
    Path externalConfigurationDirectory = JpsProjectConfigurationLoading.getExternalConfigurationDirectoryFromSystemProperty();
    return loadProject(projectPath, externalConfigurationDirectory, pathVariables, loadUnloadedModules);
  }

  @Override
  public @NotNull JpsProject loadProject(@NotNull Path projectPath,
                                         @Nullable Path externalConfigurationDirectory,
                                         @NotNull Map<String, String> pathVariables,
                                         boolean loadUnloadedModules) throws IOException {
    JpsSerializationViaWorkspaceModel serializationViaWorkspaceModel = JpsSerializationViaWorkspaceModel.getInstance();
    if (serializationViaWorkspaceModel != null) {
      return serializationViaWorkspaceModel.loadProject(projectPath, externalConfigurationDirectory, pathVariables, loadUnloadedModules);
    }

    JpsModel model = JpsElementFactory.getInstance().createModel();
    JpsProject project = model.getProject();
    JpsProjectLoader.loadProject(project, pathVariables, JpsPathMapper.IDENTITY, projectPath, loadUnloadedModules,
                                 externalConfigurationDirectory);
    return model.getProject();
  }
}
