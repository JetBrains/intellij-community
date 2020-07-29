// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.cmdline;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ParameterizedRunnable;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.serialization.JpsSerializationManager;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public final class JpsModelLoaderImpl implements JpsModelLoader {
  private static final Logger LOG = Logger.getInstance(JpsModelLoaderImpl.class);
  private final String myProjectPath;
  private final String myGlobalOptionsPath;
  private final boolean myLoadUnloadedModules;
  private final ParameterizedRunnable<? super JpsModel> myModelInitializer;

  public JpsModelLoaderImpl(String projectPath,
                            String globalOptionsPath,
                            boolean loadUnloadedModules,
                            @Nullable ParameterizedRunnable<? super JpsModel> initializer) {
    myProjectPath = projectPath;
    myGlobalOptionsPath = globalOptionsPath;
    myLoadUnloadedModules = loadUnloadedModules;
    myModelInitializer = initializer;
  }

  @Override
  public JpsModel loadModel() throws IOException {
    final long start = System.nanoTime();
    LOG.info("Loading model: project path = " + myProjectPath + ", global options path = " + myGlobalOptionsPath);
    final JpsModel model = JpsSerializationManager.getInstance().loadModel(myProjectPath, myGlobalOptionsPath, myLoadUnloadedModules);
    if (myModelInitializer != null) {
      myModelInitializer.run(model);
    }
    final long loadTime = System.nanoTime() - start;
    LOG.info("Model loaded in " + TimeUnit.NANOSECONDS.toMillis(loadTime) + " ms");
    LOG.info("Project has " + model.getProject().getModules().size() + " modules, " + model.getProject().getLibraryCollection().getLibraries().size() + " libraries");
    return model;
  }
}
