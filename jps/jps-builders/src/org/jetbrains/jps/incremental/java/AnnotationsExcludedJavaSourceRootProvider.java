// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
public final class AnnotationsExcludedJavaSourceRootProvider extends ExcludedJavaSourceRootProvider{
  @Override
  public boolean isExcludedFromCompilation(@NotNull JpsModule module, @NotNull JpsModuleSourceRoot root) {
    final JpsJavaCompilerConfiguration compilerConfig = JpsJavaExtensionService.getInstance().getCompilerConfiguration(module.getProject());
    final ProcessorConfigProfile profile = compilerConfig.getAnnotationProcessingProfile(module);
    if (!profile.isEnabled()) {
      return false;
    }

    final File outputDir =
      ProjectPaths.getAnnotationProcessorGeneratedSourcesOutputDir(module, JavaSourceRootType.TEST_SOURCE == root.getRootType(), profile);

    return outputDir != null && FileUtil.filesEqual(outputDir, root.getFile());
  }
}
