/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import org.jetbrains.annotations.NonNls;

/**
 * @author peter
 */
public class OffsetKey {
  private final String myName; // for debug purposes only
  private final boolean myMoveableToRight;

  private OffsetKey(@NonNls String name, final boolean moveableToRight) {
    myName = name;
    myMoveableToRight = moveableToRight;
  }

  public String toString() {
    return myName;
  }

  public boolean isMoveableToRight() {
    return myMoveableToRight;
  }

  public static OffsetKey create(@NonNls String name) {
    return create(name, true);
  }

  public static OffsetKey create(@NonNls String name, final boolean moveableToRight) {
    return new OffsetKey(name, moveableToRight);
  }
}
