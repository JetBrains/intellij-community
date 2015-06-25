/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.platform.loader.impl;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.platform.loader.PlatformLoader;
import org.jetbrains.platform.loader.impl.repository.DevelopmentRepository;
import org.jetbrains.platform.loader.impl.repository.PlatformRepositoryBase;
import org.jetbrains.platform.loader.impl.repository.ProductionRepository;
import org.jetbrains.platform.loader.repository.PlatformRepository;

import java.io.File;

/**
 * @author nik
 */
public class PlatformLoaderImpl extends PlatformLoader {
  private static final Logger LOG = Logger.getInstance(PlatformLoaderImpl.class);
  private final PlatformRepositoryBase myRepository;

  public PlatformLoaderImpl() {
    String pathForClass = PathManager.getJarPathForClass(getClass());
    if (pathForClass == null) throw new AssertionError("Cannot get path for " + getClass().getName() + " class");
    File platformLoaderModuleRoot = new File(pathForClass);
    if (platformLoaderModuleRoot.isFile()) {
      myRepository = new ProductionRepository(platformLoaderModuleRoot.getParentFile().getParentFile());
    }
    else {
      File outputDir = platformLoaderModuleRoot.getParentFile().getParentFile();
      File projectHome = outputDir.getParentFile();
      while (projectHome != null && !new File(projectHome, ".idea").isDirectory()) {
        projectHome = projectHome.getParentFile();
      }
      if (projectHome == null) {
        throw new AssertionError("IDEA project above " + outputDir + " output directory cannot be found");
      }
      myRepository = new DevelopmentRepository(projectHome, outputDir);
    }
  }

  @NotNull
  @Override
  public PlatformRepository getRepository() {
    return myRepository;
  }
}
