// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.artifacts.instructions;

import com.dynatrace.hash4j.hashing.HashStream64;
import com.intellij.openapi.util.io.FileFilters;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.CompileContext;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * @see ArtifactRootCopyingHandlerProvider
 */
@ApiStatus.Internal
public abstract class FileCopyingHandler {
  public abstract void copyFile(@NotNull File from, @NotNull File to, @NotNull CompileContext context) throws IOException;

  /**
   * Write configuration on which this handler depends. If the output produced by this method changes, the corresponding artifact will be rebuilt
   * from the scratch.
   *
   * @see org.jetbrains.jps.builders.BuildTarget#writeConfiguration(ProjectDescriptor, PrintWriter)
   */
  public abstract void writeConfiguration(@NotNull HashStream64 hash);

  public @NotNull FileFilter createFileFilter() {
    return FileFilters.EVERYTHING;
  }
}
