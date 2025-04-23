// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.bazel.impl;

import org.jetbrains.jps.bazel.BuilderArgs;
import org.jetbrains.jps.bazel.DiagnosticSink;
import org.jetbrains.jps.bazel.ExitCode;
import org.jetbrains.jps.bazel.runner.CompilerRunner;
import org.jetbrains.jps.bazel.runner.OutputSink;
import org.jetbrains.jps.dependency.NodeSource;

public class KotlinCompilerRunner implements CompilerRunner {
  @Override
  public String getName() {
    return "Kotlinc Runner";
  }

  @Override
  public boolean canCompile(NodeSource src) {
    return src.toString().endsWith(".kt");
  }

  @Override
  public ExitCode compile(Iterable<NodeSource> sources, BuilderArgs args, DiagnosticSink diagnostic, OutputSink out) {
    return ExitCode.OK; // todo
  }
}
