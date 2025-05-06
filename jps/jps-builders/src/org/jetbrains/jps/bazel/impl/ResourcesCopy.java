// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.bazel.impl;

import org.jetbrains.jps.bazel.BuildContext;
import org.jetbrains.jps.bazel.DiagnosticSink;
import org.jetbrains.jps.bazel.ExitCode;
import org.jetbrains.jps.bazel.StorageManager;
import org.jetbrains.jps.bazel.runner.CompilerRunner;
import org.jetbrains.jps.bazel.runner.OutputSink;
import org.jetbrains.jps.dependency.NodeSource;

public class ResourcesCopy implements CompilerRunner {
  public ResourcesCopy(BuildContext context, StorageManager storageManager) {
  }

  @Override
  public String getName() {
    return "Resources Copy";
  }

  @Override
  public boolean canCompile(NodeSource src) {
    return false; // todo: resource patterns configuration
  }

  @Override
  public ExitCode compile(Iterable<NodeSource> sources, DiagnosticSink diagnostic, OutputSink out) {
    return ExitCode.OK; // todo
  }
}
