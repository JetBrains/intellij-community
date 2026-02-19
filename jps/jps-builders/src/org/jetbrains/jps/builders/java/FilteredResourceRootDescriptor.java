// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.java;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.ResourcesTarget;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerConfiguration;

import java.io.File;
import java.io.FileFilter;
import java.nio.file.Path;
import java.util.Set;

@ApiStatus.Internal
public final class FilteredResourceRootDescriptor extends ResourceRootDescriptor {
  public FilteredResourceRootDescriptor(@NotNull File root,
                                        @NotNull ResourcesTarget target,
                                        @NotNull String packagePrefix,
                                        @NotNull Set<Path> excludes,
                                        @NotNull FileFilter filterForExcludedPatterns) {
    super(root, target, packagePrefix, excludes, filterForExcludedPatterns);
  }

  @Override
  public @NotNull FileFilter createFileFilter() {
    FileFilter baseFilter = super.createFileFilter();
    final JpsJavaCompilerConfiguration configuration = JpsJavaExtensionService.getInstance().getCompilerConfiguration(getTarget().getModule().getProject());
    final JavadocSnippetsSkipFilter snippetsSkipFilter = new JavadocSnippetsSkipFilter(getRootFile());
    return file -> baseFilter.accept(file) && configuration.isResourceFile(file, getRootFile()) && snippetsSkipFilter.accept(file);
  }
}
