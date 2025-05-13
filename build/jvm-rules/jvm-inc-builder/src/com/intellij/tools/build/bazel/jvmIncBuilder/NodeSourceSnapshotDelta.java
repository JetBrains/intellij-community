// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.NodeSource;

public interface NodeSourceSnapshotDelta extends ElementSnapshotDelta<NodeSource> {

  @Override
  @NotNull
  NodeSourceSnapshot getBaseSnapshot();

  @Override
  @NotNull
  Iterable<@NotNull NodeSource> getDeleted();

  @Override
  @NotNull
  Iterable<@NotNull NodeSource> getModified(); // sources to recompile: both changed and newly added
  
  boolean isRecompile(@NotNull NodeSource src);

  void markRecompile(@NotNull NodeSource src);

  boolean isRecompileAll();

  default void markRecompileAll() {
    for (NodeSource s : getBaseSnapshot().getElements()) {
      markRecompile(s);
    }
  }

  boolean hasChanges();

  /**
   * Provides a SourceSnapshot view for the delta where digests for files marked for recompilation are ignored
   */
  NodeSourceSnapshot asSnapshot();

}
