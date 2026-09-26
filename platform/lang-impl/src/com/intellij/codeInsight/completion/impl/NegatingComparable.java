// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.impl;

import com.intellij.psi.ForceableComparable;
import org.jetbrains.annotations.NotNull;

public class NegatingComparable<T extends NegatingComparable<T>> implements Comparable<T>, ForceableComparable {
  private final Comparable myWeigh;

  public NegatingComparable(Comparable weigh) {
    myWeigh = weigh;
  }

  @Override
  public void force() {
    if (myWeigh instanceof ForceableComparable) {
      ((ForceableComparable)myWeigh).force();
    }
  }

  @Override
  public int compareTo(@NotNull T o) {
    final Comparable w1 = myWeigh;
    final Comparable w2 = ((NegatingComparable<?>)o).myWeigh;
    if (w1 == null && w2 == null) return 0;
    if (w1 == null) return 1;
    if (w2 == null) return -1;

    return -w1.compareTo(w2);
  }

  @Override
  public String toString() {
    return String.valueOf(myWeigh);
  }
}
