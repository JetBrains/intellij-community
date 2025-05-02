// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.bazel;

import org.jetbrains.annotations.NonNls;

import java.nio.file.Path;

public interface BuildProcessLogger {
  BuildProcessLogger EMPTY = new BuildProcessLogger() {
    @Override
    public boolean isEnabled() {
      return false;
    }

    @Override
    public void logDeletedPaths(Iterable<String> paths) {
    }

    @Override
    public void logCompiledPaths(Iterable<Path> files, String builderId, String description) {
    }
  };
  
  boolean isEnabled();

  void logDeletedPaths(Iterable<String> paths);

  void logCompiledPaths(Iterable<Path> files, @NonNls String builderId, @NonNls String description);
}
