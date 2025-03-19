// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle.arrangement.engine;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class ArrangementMoveInfo {

  private final int myOldStart;
  private final int myOldEnd;
  private final int myNewStart;

  public ArrangementMoveInfo(int oldStart, int oldEnd, int newStart) {
    myOldStart = oldStart;
    myOldEnd = oldEnd;
    myNewStart = newStart;
  }

  public int getOldStart() {
    return myOldStart;
  }

  public int getOldEnd() {
    return myOldEnd;
  }

  public int getNewStart() {
    return myNewStart;
  }

  @Override
  public String toString() {
    return String.format("range [%d; %d) to offset %d", myOldStart, myOldEnd, myNewStart);
  }
}
