// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.fragmented;

import com.intellij.diff.util.Side;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class HighlightRange {
  private final @NotNull TextRange myBase;
  private final @NotNull TextRange myChanged;
  private final @NotNull Side mySide;

  HighlightRange(@NotNull Side side, @NotNull TextRange base, @NotNull TextRange changed) {
    mySide = side;
    myBase = base;
    myChanged = changed;
  }

  public @NotNull Side getSide() {
    return mySide;
  }

  public @NotNull TextRange getBase() {
    return myBase;
  }

  public @NotNull TextRange getChanged() {
    return myChanged;
  }
}
