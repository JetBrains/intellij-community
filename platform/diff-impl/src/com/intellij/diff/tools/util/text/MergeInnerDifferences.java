// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.util.text;

import com.intellij.diff.util.ThreeSide;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class MergeInnerDifferences {
  private final @Nullable List<TextRange> myLeft;
  private final @Nullable List<TextRange> myBase;
  private final @Nullable List<TextRange> myRight;

  public MergeInnerDifferences(@Nullable List<TextRange> left, @Nullable List<TextRange> base, @Nullable List<TextRange> right) {
    myLeft = left;
    myBase = base;
    myRight = right;
  }

  /**
   * NB: ranges might overlap and might be not in order
   */
  public @Nullable List<TextRange> get(@NotNull ThreeSide side) {
    return side.select(myLeft, myBase, myRight);
  }
}
