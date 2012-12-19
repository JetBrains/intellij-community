/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.cmdline;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ParameterizedRunnable;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.serialization.JpsGlobalLoader;
import org.jetbrains.jps.model.serialization.JpsProjectLoader;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * @author nik
 */
public class JpsModelLoaderImpl implements JpsModelLoader {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.cmdline.JpsModelLoaderImpl");
  private final String myProjectPath;
  private final String myGlobalOptionsPath;
  private final Map<String, String> myPathVars;
  private final ParameterizedRunnable<JpsModel> myModelInitializer;

  public JpsModelLoaderImpl(String projectPath, String globalOptionsPath, Map<String, String> pathVars,
                            ParameterizedRunnable<JpsModel> initializer) {
    myProjectPath = projectPath;
    myGlobalOptionsPath = globalOptionsPath;
    myPathVars = pathVars;
    myModelInitializer = initializer;
  }

  @Override
  public JpsModel loadModel() {
    final long start = System.currentTimeMillis();
    try {
      final JpsModel model = JpsElementFactory.getInstance().createModel();
      try {
        if (myGlobalOptionsPath != null) {
          JpsGlobalLoader.loadGlobalSettings(model.getGlobal(), myPathVars, myGlobalOptionsPath);
        }
        JpsProjectLoader.loadProject(model.getProject(), myPathVars, myProjectPath);
        if (myModelInitializer != null) {
          myModelInitializer.run(model);
        }
        LOG.info("New JPS model: " + model.getProject().getModules().size() + " modules, " + model.getProject().getLibraryCollection().getLibraries().size() + " libraries");
      }
      catch (IOException e) {
        LOG.info(e);
      }
      return model;
    }
    finally {
      final long loadTime = System.currentTimeMillis() - start;
      LOG.info("New JPS model: project " + myProjectPath + " loaded in " + loadTime + " ms");
    }
  }

  private static boolean isDirectoryBased(File projectFile) {
    return !(projectFile.isFile() && projectFile.getName().endsWith(".ipr"));
  }
}
