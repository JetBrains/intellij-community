// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.ExternalizableGraphElement;

public interface ElementSnapshotDelta<T extends ExternalizableGraphElement> {
  @NotNull
  ElementSnapshot<T> getBaseSnapshot();

  @NotNull
  Iterable<@NotNull T> getDeleted();

  /**
   * @return both changed or added elements
   */
  @NotNull
  Iterable<@NotNull T> getModified();

  /**
   * @return elements that were present in the past, existing in present, but changed their state
   */
  @NotNull
  Iterable<@NotNull T> getChanged();
}
