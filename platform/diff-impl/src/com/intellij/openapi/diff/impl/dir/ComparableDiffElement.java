// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.dir;

import com.intellij.ide.diff.DiffElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ComparableDiffElement {
  @Nullable
  Boolean isContentEqual(@NotNull DiffElement<?> other);
}
