// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.model.serialization.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.JpsGlobal;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.serialization.*;

import java.io.IOException;
import java.util.Map;

public final class JpsSerializationManagerImpl extends JpsSerializationManager {
  @NotNull
  @Override
  public JpsModel loadModel(@NotNull String projectPath, @Nullable String optionsPath, boolean loadUnloadedModules)
    throws IOException {
    JpsModel model = JpsElementFactory.getInstance().createModel();
    if (optionsPath != null) {
      JpsGlobalLoader.loadGlobalSettings(model.getGlobal(), optionsPath);
    }
    Map<String, String> pathVariables = JpsModelSerializationDataService.computeAllPathVariables(model.getGlobal());
    JpsProjectLoader.loadProject(model.getProject(), pathVariables, projectPath, loadUnloadedModules);
    return model;
  }

  @NotNull
  @Override
  public JpsProject loadProject(@NotNull String projectPath, @NotNull Map<String, String> pathVariables) throws IOException {
    JpsModel model = JpsElementFactory.getInstance().createModel();
    JpsProjectLoader.loadProject(model.getProject(), pathVariables, projectPath);
    return model.getProject();
  }

  @Override
  public void saveGlobalSettings(@NotNull JpsGlobal global, @NotNull String optionsPath) throws IOException {
    JpsGlobalElementSaver.saveGlobalElement(global, optionsPath);
  }
}
