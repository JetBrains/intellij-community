// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.bazel.runner;

import org.jetbrains.jps.bazel.BuilderArgs;
import org.jetbrains.jps.bazel.DiagnosticSink;
import org.jetbrains.jps.bazel.ExitCode;
import org.jetbrains.jps.dependency.NodeSource;

public interface CompilerRunner extends Runner{
  boolean canCompile(NodeSource src);

  ExitCode compile(Iterable<NodeSource> sources, BuilderArgs args, DiagnosticSink diagnostic, OutputSink out);
}
