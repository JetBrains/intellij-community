// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.bazel.runner;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.bazel.BuildContext;
import org.jetbrains.jps.bazel.BuildProcessLogger;
import org.jetbrains.jps.bazel.DiagnosticSink;
import org.jetbrains.jps.bazel.ExitCode;
import org.jetbrains.jps.dependency.NodeSource;
import org.jetbrains.jps.dependency.NodeSourcePathMapper;

import static org.jetbrains.jps.javac.Iterators.isEmpty;
import static org.jetbrains.jps.javac.Iterators.map;

public interface CompilerRunner extends Runner{
  boolean canCompile(NodeSource src);

  ExitCode compile(Iterable<NodeSource> sources, DiagnosticSink diagnostic, OutputSink out);

  default void logCompiledFiles(BuildContext context, Iterable<@NotNull NodeSource> toCompile) {
    if (!context.isRebuild()) {
      BuildProcessLogger logger = context.getBuildLogger();
      if (logger.isEnabled() && !isEmpty(toCompile)) {
        NodeSourcePathMapper pathMapper = context.getGraphConfig().getPathMapper();
        logger.logCompiledPaths(map(toCompile, pathMapper::toPath), getName(), "Compiling files:");
      }
    }
  }

}
