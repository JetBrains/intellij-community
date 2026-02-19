// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.logging;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;

public interface ProjectBuilderLogger {
  boolean isEnabled();

  void logDeletedFiles(Collection<String> paths);

  void logCompiledFiles(Collection<File> files, @NonNls String builderId, @NonNls String description) throws IOException;

  void logCompiled(@NotNull Collection<Path> files, @NonNls String builderId, @NonNls String description) throws IOException;

  void logCompiledPaths(@NotNull Collection<String> paths, @NonNls String builderId, @NonNls String description) throws IOException;
}
