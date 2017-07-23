/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.model.serialization;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.util.PathUtil;
import com.intellij.util.SystemProperties;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsModelTestCase;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author nik
 */
public abstract class JpsSerializationTestCase extends JpsModelTestCase {
  private String myProjectHomePath;

  protected void loadProject(final String relativePath) {
    loadProjectByAbsolutePath(getTestDataFileAbsolutePath(relativePath));
  }

  protected void loadProjectByAbsolutePath(String path) {
    myProjectHomePath = FileUtilRt.toSystemIndependentName(path);
    if (myProjectHomePath.endsWith(".ipr")) {
      myProjectHomePath = PathUtil.getParentPath(myProjectHomePath);
    }
    try {
      JpsProjectLoader.loadProject(myProject, getPathVariables(), path);
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
      JpsGlobalLoader.loadGlobalSettings(myModel.getGlobal(), optionsPath);
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

  protected static Element loadModuleRootTag(@NotNull Path imlFile) {
    JpsMacroExpander expander = JpsProjectLoader.createModuleMacroExpander(Collections.emptyMap(), imlFile);
    return JpsLoaderBase.loadRootElement(imlFile, expander);
  }
}
