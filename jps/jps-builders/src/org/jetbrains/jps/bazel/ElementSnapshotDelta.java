// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.bazel;

import org.jetbrains.annotations.NotNull;

public interface ElementSnapshotDelta<T> {
  @NotNull
  ElementSnapshot<T> getBaseSnapshot();

  @NotNull
  Iterable<@NotNull T> getDeleted();

  @NotNull
  Iterable<@NotNull T> getModified();
}
