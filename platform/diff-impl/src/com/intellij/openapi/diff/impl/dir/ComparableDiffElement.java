// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diff.impl.dir;

import com.intellij.ide.diff.DiffElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public interface ComparableDiffElement {
  @Nullable
  Boolean isContentEqual(@NotNull DiffElement<?> other);

  default void prepare(@Nullable DiffElement<?> other) {
  }
}
