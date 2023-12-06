// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public final class AlignmentCyclesDetector {
  private final int myTotalAlignmentsCount;

  private final Map<OffsetPair,Integer> myRealignmentCounts = new HashMap<>();

  public AlignmentCyclesDetector(int totalAlignmentsCount) {
    myTotalAlignmentsCount = totalAlignmentsCount;
  }

  public boolean registerRealignment(@NotNull LeafBlockWrapper offsetResponsibleBlock, @NotNull LeafBlockWrapper currentBlock) {
    OffsetPair pair = new OffsetPair(offsetResponsibleBlock.getStartOffset(), currentBlock.getStartOffset());
    int count = myRealignmentCounts.containsKey(pair) ? myRealignmentCounts.get(pair) + 1 : 0;
    if (count > myTotalAlignmentsCount) {
      return false;
    }
    myRealignmentCounts.put(pair, count);
    return true;
  }


  private record OffsetPair(int first, int second) {
    private static final int MAX_VALUE = Integer.MAX_VALUE >>> 16;

    @Override
    public int hashCode() {
      int a = first >= MAX_VALUE ? first % MAX_VALUE : first;
      int b = second >= MAX_VALUE ? second % MAX_VALUE : second;
      int sum = a + b;
      if (sum >= MAX_VALUE) sum = sum % MAX_VALUE;
      return sum * (sum + 1) / 2 + a;
    }
  }
}
