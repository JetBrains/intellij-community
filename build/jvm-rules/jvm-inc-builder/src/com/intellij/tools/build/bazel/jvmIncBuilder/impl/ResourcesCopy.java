// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder.impl;

import com.intellij.tools.build.bazel.jvmIncBuilder.BuildContext;
import com.intellij.tools.build.bazel.jvmIncBuilder.DiagnosticSink;
import com.intellij.tools.build.bazel.jvmIncBuilder.ExitCode;
import com.intellij.tools.build.bazel.jvmIncBuilder.StorageManager;
import com.intellij.tools.build.bazel.jvmIncBuilder.runner.CompilerRunner;
import com.intellij.tools.build.bazel.jvmIncBuilder.runner.OutputSink;
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
