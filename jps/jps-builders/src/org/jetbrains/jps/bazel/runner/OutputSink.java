// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.bazel.runner;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.NodeSource;

public interface OutputSink extends OutputExplorer{

  interface OutputFile {

    enum Kind {
      bytecode, source, other
    }

    Kind getKind();

    @NotNull String getPath();

    byte @NotNull [] getContent();

    default boolean isFromGeneratedSource() {
      return false;
    }
  }

  void addFile(OutputFile outFile, Iterable<NodeSource> originSources);
}
