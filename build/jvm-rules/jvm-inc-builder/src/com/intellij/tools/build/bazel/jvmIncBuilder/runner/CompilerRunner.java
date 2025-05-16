// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder.runner;

import com.intellij.tools.build.bazel.jvmIncBuilder.BuildContext;
import com.intellij.tools.build.bazel.jvmIncBuilder.BuildProcessLogger;
import com.intellij.tools.build.bazel.jvmIncBuilder.DiagnosticSink;
import com.intellij.tools.build.bazel.jvmIncBuilder.ExitCode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.NodeSource;
import org.jetbrains.jps.dependency.NodeSourcePathMapper;

import static org.jetbrains.jps.util.Iterators.isEmpty;
import static org.jetbrains.jps.util.Iterators.map;

public interface CompilerRunner extends Runner{
  boolean canCompile(NodeSource src);

  ExitCode compile(Iterable<NodeSource> sources, Iterable<NodeSource> deletedSources, DiagnosticSink diagnostic, OutputSink out);

  default void logCompiledFiles(BuildContext context, Iterable<@NotNull NodeSource> toCompile) {
    if (!context.isRebuild()) {
      BuildProcessLogger logger = context.getBuildLogger();
      if (logger.isEnabled() && !isEmpty(toCompile)) {
        NodeSourcePathMapper pathMapper = context.getPathMapper();
        logger.logCompiledPaths(map(toCompile, pathMapper::toPath), getName(), "Compiling files:");
      }
    }
  }

}
