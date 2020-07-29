// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.ex;

import org.jetbrains.annotations.NotNull;

public class ChangedLines<T> {
  // VisualPosition.line
  public final int line1;
  public final int line2;
  public final byte type;
  @NotNull public final T flags;

  ChangedLines(int line1, int line2, byte type, @NotNull T flags) {
    this.line1 = line1;
    this.line2 = line2;
    this.type = type;
    this.flags = flags;
  }
}
