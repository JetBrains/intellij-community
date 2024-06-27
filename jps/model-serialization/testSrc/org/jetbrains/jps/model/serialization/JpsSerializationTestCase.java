// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.serialization;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.util.PathUtil;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsModelTestCase;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * @deprecated use {@link JpsProjectData} instead
 */
@Deprecated(forRemoval = true)
public abstract class JpsSerializationTestCase extends JpsModelTestCase {
  private String myProjectHomePath;

  protected void loadProject(final String relativePath) {
    loadProjectByAbsolutePath(getTestDataFileAbsolutePath(relativePath));
  }
  
  protected void loadProject(@NotNull String relativePath, @NotNull String externalStorageRelativePath) {
    loadProjectByAbsolutePath(getTestDataFileAbsolutePath(relativePath), Paths.get(getTestDataFileAbsolutePath(externalStorageRelativePath)));
  }

  protected void loadProjectByAbsolutePath(String path) {
    loadProjectByAbsolutePath(path, null);
  }

  protected final void loadProjectByAbsolutePath(String path, Path externalConfigurationDirectory) {
    myProjectHomePath = FileUtilRt.toSystemIndependentName(path);
    if (myProjectHomePath.endsWith(".ipr")) {
      myProjectHomePath = PathUtil.getParentPath(myProjectHomePath);
    }
    try {
      Map<String, String> pathVariables = getPathVariables();
      Path projectPath = Paths.get(path);
      JpsProjectLoader.loadProject(myProject, pathVariables, JpsPathMapper.IDENTITY, projectPath, externalConfigurationDirectory,
                                   Runnable::run, false);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected String getUrl(String relativePath) {
    return VfsUtilCore.pathToUrl(getAbsolutePath(relativePath));
  }

  protected String getAbsolutePath(String relativePath) {
    return myProjectHomePath + "/" + relativePath;
  }

  protected void loadGlobalSettings(final String optionsDir) {
    try {
      String optionsPath = getTestDataFileAbsolutePath(optionsDir);
      Map<String,String> pathVariables = getPathVariables();
      JpsPathVariablesConfiguration configuration = JpsModelSerializationDataService.getOrCreatePathVariablesConfiguration(myModel.getGlobal());
      for (Map.Entry<String, String> entry : pathVariables.entrySet()) {
        configuration.addPathVariable(entry.getKey(), entry.getValue());
      }
      JpsGlobalSettingsLoading.loadGlobalSettings(myModel.getGlobal(), Paths.get(optionsPath));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected Map<String, String> getPathVariables() {
    Map<String, String> variables = new HashMap<>();
    variables.put(PathMacroUtil.APPLICATION_HOME_DIR, PathManager.getHomePath());
    variables.put(PathMacroUtil.USER_HOME_NAME, SystemProperties.getUserHome());
    return variables;
  }

  protected String getTestDataFileAbsolutePath(@NotNull String relativePath) {
    return PathManagerEx.findFileUnderProjectHome(relativePath, getClass()).getAbsolutePath();
  }

  @NotNull
  protected Path getTestDataAbsoluteFile(@NotNull String relativePath) {
    return Paths.get(getTestDataFileAbsolutePath(relativePath));
  }
}
