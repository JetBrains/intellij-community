// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.artifacts.instructions;

import com.dynatrace.hash4j.hashing.HashSink;
import com.intellij.openapi.util.io.FileFilters;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.CompileContext;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;

/**
 * @see ArtifactRootCopyingHandlerProvider
 */
@ApiStatus.Internal
public abstract class FileCopyingHandler {
  public abstract void copyFile(@NotNull File from, @NotNull File to, @NotNull CompileContext context) throws IOException;

  /**
   * Write configuration on which this handler depends.
   * If the output produced by this method changes, the corresponding artifact will be rebuilt from scratch.
   *
   * @see org.jetbrains.jps.builders.BuildTargetHashSupplier
   */
  public abstract void writeConfiguration(@NotNull HashSink hash);

  public @NotNull FileFilter createFileFilter() {
    return FileFilters.EVERYTHING;
  }
}
