// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class OffsetKey {
  private final String myName; // for debug purposes only
  private final boolean myMovableToRight;

  private OffsetKey(@NonNls String name, final boolean movableToRight) {
    myName = name;
    myMovableToRight = movableToRight;
  }

  @Override
  public String toString() {
    return myName;
  }

  public boolean isMovableToRight() {
    return myMovableToRight;
  }

  public static @NotNull OffsetKey create(@NonNls String name) {
    return create(name, true);
  }

  public static @NotNull OffsetKey create(@NonNls String name, final boolean movableToRight) {
    return new OffsetKey(name, movableToRight);
  }
}
