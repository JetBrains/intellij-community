// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.chainsSearch;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

public final class ChainRelevance implements Comparable<ChainRelevance> {
  private final int myChainSize;
  private final int myUnreachableParameterCount;
  private final int myParametersInContext;

  public ChainRelevance(final int chainSize,
                        final int unreachableParameterCount,
                        final int parametersInContext) {
    myChainSize = chainSize;
    myUnreachableParameterCount = unreachableParameterCount;
    myParametersInContext = parametersInContext;
  }

  @TestOnly
  public int getChainSize() {
    return myChainSize;
  }

  @TestOnly
  public int getUnreachableParameterCount() {
    return myUnreachableParameterCount;
  }

  @TestOnly
  public int getParametersInContext() {
    return myParametersInContext;
  }

  @Override
  public int compareTo(final @NotNull ChainRelevance that) {
    int sub = Integer.compare(myChainSize, that.myChainSize);
    if (sub != 0) return sub;
    sub = Integer.compare(myUnreachableParameterCount, that.myUnreachableParameterCount);
    if (sub != 0) return sub;
    return -Integer.compare(myParametersInContext, that.myParametersInContext);
  }

  @Override
  public String toString() {
    return "{\"chain_size\": " + myChainSize +
           ", \"unreachable_params\": " + myUnreachableParameterCount +
           ", \"parameters_in_context\": " + myParametersInContext + "}";
  }
}
