// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.util;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class Pair<A, B> {
  public final A first;
  public final B second;

  public Pair(A first, B second) {
    this.first = first;
    this.second = second;
  }

  @NotNull
  public static <A, B> Pair<A, B> create(A first, B second) {
    return new Pair<>(first, second);
  }

  @Override
  public final boolean equals(Object o) {
    return o instanceof Pair && Objects.equals(first, ((Pair<?, ?>)o).first) && Objects.equals(second, ((Pair<?, ?>)o).second);
  }

  @Override
  public int hashCode() {
    int result = first != null ? first.hashCode() : 0;
    result = 31 * result + (second != null ? second.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "<" + first + "," + second + ">";
  }
}