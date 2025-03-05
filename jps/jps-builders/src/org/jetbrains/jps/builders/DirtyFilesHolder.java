// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;

/**
 * Provides list of files under {@link BuildTarget#computeRootDescriptors source roots} of a target which were modified or deleted since the
 * previous build.
 *
 * @see org.jetbrains.jps.incremental.TargetBuilder#build
 * @see org.jetbrains.jps.incremental.ModuleLevelBuilder#build
 */
public interface DirtyFilesHolder<R extends BuildRootDescriptor, T extends BuildTarget<R>> {
  void processDirtyFiles(@NotNull FileProcessor<R, T> processor) throws IOException;

  boolean hasDirtyFiles() throws IOException;

  boolean hasRemovedFiles();

  /**
   * @deprecated Use {@link #getRemoved(BuildTarget)}
   */
  @NotNull
  @Unmodifiable
  Collection<@NotNull String> getRemovedFiles(@NotNull T target);

  @NotNull
  @Unmodifiable
  Collection<@NotNull Path> getRemoved(@NotNull T target);
}
