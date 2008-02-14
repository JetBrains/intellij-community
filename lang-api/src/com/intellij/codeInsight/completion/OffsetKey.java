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

  private OffsetKey(@NonNls String name) {
    myName = name;
  }

  public String toString() {
    return myName;
  }

  public static OffsetKey create(@NonNls String name) {
    return new OffsetKey(name);
  }
}
