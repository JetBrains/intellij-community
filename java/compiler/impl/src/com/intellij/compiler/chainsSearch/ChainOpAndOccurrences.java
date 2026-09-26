// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.chainsSearch;

import org.jetbrains.annotations.NotNull;

public class ChainOpAndOccurrences<T extends RefChainOperation> implements Comparable<ChainOpAndOccurrences> {
  private final T myOp;
  private final int myOccurrences;

  public ChainOpAndOccurrences(final T op, final int occurrences) {
    myOp = op;
    myOccurrences = occurrences;
  }

  public T getOperation() {
    return myOp;
  }

  public int getOccurrenceCount() {
    return myOccurrences;
  }

  @Override
  public int compareTo(final @NotNull ChainOpAndOccurrences that) {
    final int sub = -getOccurrenceCount() + that.getOccurrenceCount();
    if (sub != 0) {
      return sub;
    }
    return myOp.hashCode() - that.myOp.hashCode();
  }

  @Override
  public String toString() {
    return getOccurrenceCount() + " for " + myOp;
  }
}
