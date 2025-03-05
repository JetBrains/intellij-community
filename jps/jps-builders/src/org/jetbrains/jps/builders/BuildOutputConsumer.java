// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

/**
 * Use methods of this interface to register files produced by a builder in the build system. This will allow other builders to process
 * generated files and also update the source-to-output mapping. The build system deletes output files corresponding to changed or deleted
 * source files before the next build starts. Also, all output files registered in the mapping are cleared on forced recompilation (rebuild).
 */
public interface BuildOutputConsumer {
  /**
   * Notifies the build system that {@code outputFile} was produced from {@code sourcePaths}.
   */
  void registerOutputFile(@NotNull File outputFile, @NotNull @Unmodifiable Collection<@NotNull String> sourcePaths) throws IOException;

  /**
   * Notifies the build system that the entire contents of {@code outputDir} was produced from {@code sourcePaths}. Note that
   * if one of {@code sourcePaths} changes after the build is finished the {@code outputDir} will be deleted completely before
   * the next build starts so don't use this method if {@code outputDir} contains source files or files produced by other builders.
   */
  void registerOutputDirectory(@NotNull File outputDir, @NotNull @Unmodifiable Collection<@NotNull String> sourcePaths) throws IOException;
}
