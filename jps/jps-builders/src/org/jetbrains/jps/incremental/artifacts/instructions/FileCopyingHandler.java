// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.artifacts.instructions;

import com.intellij.openapi.util.io.FileFilters;
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
public abstract class FileCopyingHandler {
  public abstract void copyFile(@NotNull File from, @NotNull File to, @NotNull CompileContext context) throws IOException;

  /**
   * Write configuration on which this handler depends. If the output produced by this method changes, the corresponding artifact will be rebuilt
   * from the scratch.
   *
   * @see org.jetbrains.jps.builders.BuildTarget#writeConfiguration(ProjectDescriptor, PrintWriter)
   */
  public abstract void writeConfiguration(@NotNull PrintWriter out);

  public @NotNull FileFilter createFileFilter() {
    return FileFilters.EVERYTHING;
  }
}
