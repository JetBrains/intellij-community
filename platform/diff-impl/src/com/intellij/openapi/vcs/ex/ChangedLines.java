// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.ex;

import org.jetbrains.annotations.NotNull;

public class ChangedLines<T> {
  // VisualPosition.line
  public final int y1;
  public final int y2;
  public final byte type;
  public final @NotNull T flags;

  ChangedLines(int y1, int y2, byte type, @NotNull T flags) {
    this.y1 = y1;
    this.y2 = y2;
    this.type = type;
    this.flags = flags;
  }
}
