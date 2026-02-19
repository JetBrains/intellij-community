// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.impl.nullaway;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.execution.filters.ConsoleFilterProvider;
import com.intellij.execution.filters.Filter;
import com.intellij.java.library.JavaLibraryUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNullByDefault;

/// Provides [NullAwayFilter] when [NullAway checker](https://github.com/uber/NullAway) is configured in the project.
@NotNullByDefault
class NullAwayFilterProvider implements ConsoleFilterProvider {
  @Override
  public Filter[] getDefaultFilters(Project project) {
    if (hasNullAwayPlugin(project)) {
      return new Filter[]{new NullAwayFilter()};
    }
    else {
      return Filter.EMPTY_ARRAY;
    }
  }

  /// Returns true when NullAway plugin is configured.
  /// Checks whether NullAway library is added to the project or the annotation processor path contains NullAway library.
  /// Note:
  ///  - In maven projects NullAway library is not added to libraries even when null-away is configured as a plugin
  /// for error-prone annotation processor in a maven build.
  ///  - In bazel projects NullAway library is not added to the annotation processor path even when NullAway is configured as a plugin
  /// for error-prone annotation processor in a bazel build.
  ///
  /// @return true when NullAway plugin is configured.
  private static boolean hasNullAwayPlugin(Project project) {
    var hasNullAwayLib = ReadAction.compute(() -> JavaLibraryUtil.hasLibraryJar(project, "com.uber.nullaway:nullaway"));
    if (hasNullAwayLib) return true;

    var compilerConfiguration = CompilerConfiguration.getInstance(project);
    if (!compilerConfiguration.isAnnotationProcessorsEnabled()) return false;
    for (var module : ModuleManager.getInstance(project).getModules()) {
      var config = compilerConfiguration.getAnnotationProcessingConfiguration(module);
      if (config.isEnabled()) {
        if (config.getProcessorPath().contains("nullaway")) {
          return true;
        }
      }
    }
    return false;
  }
}
