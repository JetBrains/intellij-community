// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.bazel;

import org.jetbrains.annotations.NotNull;

public interface ElementSnapshot<T> {
  @NotNull
  Iterable<@NotNull T> getElements();

  @NotNull
  String getDigest(T elem);
}
