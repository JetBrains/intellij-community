// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.serialization.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.serialization.*;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Map;

public final class JpsSerializationManagerImpl extends JpsSerializationManager {
  @Override
  public @NotNull JpsModel loadModel(@NotNull String projectPath, @Nullable String optionsPath, boolean loadUnloadedModules) throws IOException {
    JpsModel model = JpsElementFactory.getInstance().createModel();
    if (optionsPath != null) {
      JpsGlobalLoader.loadGlobalSettings(model.getGlobal(), optionsPath);
    }
    Map<String, String> pathVariables = JpsModelSerializationDataService.computeAllPathVariables(model.getGlobal());
    JpsProjectLoader.loadProject(model.getProject(), pathVariables, model.getGlobal().getPathMapper(), Paths.get(projectPath), loadUnloadedModules);
    return model;
  }

  @Override
  public @NotNull JpsProject loadProject(@NotNull String projectPath, @NotNull Map<String, String> pathVariables) throws IOException {
    return loadProject(projectPath, pathVariables, false);
  }

  @Override
  public @NotNull JpsProject loadProject(@NotNull String projectPath, @NotNull Map<String, String> pathVariables, boolean loadUnloadedModules) throws IOException {
    JpsModel model = JpsElementFactory.getInstance().createModel();
    JpsProjectLoader.loadProject(model.getProject(), pathVariables, JpsPathMapper.IDENTITY, Paths.get(projectPath), loadUnloadedModules);
    return model.getProject();
  }
}
