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
package org.jetbrains.jps.incremental.java;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.ProjectPaths;
import org.jetbrains.jps.builders.java.ExcludedJavaSourceRootProvider;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerConfiguration;
import org.jetbrains.jps.model.java.compiler.ProcessorConfigProfile;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleSourceRoot;

import java.io.File;

/**
 * @author Eugene Zhuravlev
 */
public class AnnotationsExcludedJavaSourceRootProvider extends ExcludedJavaSourceRootProvider{
  @Override
  public boolean isExcludedFromCompilation(@NotNull JpsModule module, @NotNull JpsModuleSourceRoot root) {
    final JpsJavaCompilerConfiguration compilerConfig = JpsJavaExtensionService.getInstance().getOrCreateCompilerConfiguration(module.getProject());
    final ProcessorConfigProfile profile = compilerConfig.getAnnotationProcessingProfile(module);
    if (!profile.isEnabled()) {
      return false;
    }

    final File outputDir =
      ProjectPaths.getAnnotationProcessorGeneratedSourcesOutputDir(module, JavaSourceRootType.TEST_SOURCE == root.getRootType(), profile);

    return outputDir != null && FileUtil.filesEqual(outputDir, root.getFile());
  }
}
