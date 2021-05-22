// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.util;

import org.jetbrains.annotations.ApiStatus;

/**
 * @deprecated Use {@link  com.intellij.util.IntPair}
 */
@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2021.2")
public final class IntPair {
  public final int val1;
  public final int val2;

  public IntPair(int val1, int val2) {
    this.val1 = val1;
    this.val2 = val2;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    IntPair pair = (IntPair)o;

    if (val1 != pair.val1) return false;
    if (val2 != pair.val2) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = val1;
    result = 31 * result + val2;
    return result;
  }

  @Override
  public String toString() {
    return "{" + val1 + ", " + val2 + "}";
  }
}
