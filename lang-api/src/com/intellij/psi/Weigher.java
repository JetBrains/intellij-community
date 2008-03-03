/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public abstract class Weigher<T, Location> {
  private String myDebugName;

  public void setDebugName(final String debugName) {
    myDebugName = debugName;
  }

  public String toString() {
    return myDebugName == null ? super.toString() : myDebugName;
  }

  @Nullable public abstract Comparable weigh(@NotNull T element, Location location);
}
