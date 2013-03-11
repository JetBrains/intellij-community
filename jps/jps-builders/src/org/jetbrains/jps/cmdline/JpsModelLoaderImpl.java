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
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.serialization.JpsSerializationManager;

import java.io.IOException;

/**
 * @author nik
 */
public class JpsModelLoaderImpl implements JpsModelLoader {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.cmdline.JpsModelLoaderImpl");
  private final String myProjectPath;
  private final String myGlobalOptionsPath;
  private final ParameterizedRunnable<JpsModel> myModelInitializer;

  public JpsModelLoaderImpl(String projectPath, String globalOptionsPath, @Nullable ParameterizedRunnable<JpsModel> initializer) {
    myProjectPath = projectPath;
    myGlobalOptionsPath = globalOptionsPath;
    myModelInitializer = initializer;
  }

  @Override
  public JpsModel loadModel() throws IOException {
    final long start = System.currentTimeMillis();
    LOG.info("Loading model: project path = " + myProjectPath + ", global options path = " + myGlobalOptionsPath);
    final JpsModel model = JpsSerializationManager.getInstance().loadModel(myProjectPath, myGlobalOptionsPath);
    if (myModelInitializer != null) {
      myModelInitializer.run(model);
    }
    final long loadTime = System.currentTimeMillis() - start;
    LOG.info("Model loaded in " + loadTime + " ms");
    LOG.info("Project has " + model.getProject().getModules().size() + " modules, " + model.getProject().getLibraryCollection().getLibraries().size() + " libraries");
    return model;
  }
}
