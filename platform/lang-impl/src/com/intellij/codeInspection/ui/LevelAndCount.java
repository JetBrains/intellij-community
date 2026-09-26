// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ui;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

final class LevelAndCount {
  private final @NotNull HighlightDisplayLevel myLevel;
  private final int myCount;

  LevelAndCount(@NotNull HighlightDisplayLevel level, int count) {
    myLevel = level;
    myCount = count;
  }

  public @NotNull HighlightDisplayLevel getLevel() {
    return myLevel;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    LevelAndCount count = (LevelAndCount)o;

    if (myCount != count.myCount) return false;
    if (!Objects.equals(myLevel, count.myLevel)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myLevel.hashCode();
    result = 31 * result + myCount;
    return result;
  }

  public int getCount() {
    return myCount;
  }
}
