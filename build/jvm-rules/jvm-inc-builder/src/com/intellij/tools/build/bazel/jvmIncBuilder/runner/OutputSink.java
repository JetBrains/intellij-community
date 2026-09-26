// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder.runner;

import org.jetbrains.jps.dependency.Node;
import org.jetbrains.jps.dependency.NodeSource;

public interface OutputSink extends OutputExplorer, CompilerDataSink{

  record NodeWithSources(Node<?, ?> node, Iterable<NodeSource> sources) {}

  Iterable<NodeWithSources> getNodes();

  void addFile(OutputFile outFile, OutputOrigin origin);

  Iterable<String> getGeneratedOutputPaths(OutputOrigin.Kind originKind, OutputFile.Kind outputKind);
}
