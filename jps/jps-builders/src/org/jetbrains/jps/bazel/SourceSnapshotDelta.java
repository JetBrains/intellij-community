// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.bazel;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.NodeSource;

public interface SourceSnapshotDelta {

  @NotNull
  SourceSnapshot getBaseSnapshot();

  @NotNull
  Iterable<@NotNull NodeSource> getDeletedSources();

  @NotNull
  Iterable<@NotNull NodeSource> getSourcesToRecompile();
  
  boolean isRecompile(@NotNull NodeSource src);

  void markRecompile(@NotNull NodeSource src);

  boolean isRecompileAll();

  default void markRecompileAll() {
    for (NodeSource s : getBaseSnapshot().getSources()) {
      markRecompile(s);
    }
  }

  boolean hasChanges();

  /**
   * Provides a SourceSnapshot view for the delta where digests for files marked for recompilation are ignored
   */
  SourceSnapshot asSnapshot();

}
