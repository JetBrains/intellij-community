// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting;

import org.jetbrains.annotations.NotNull;

import java.util.*;

public class AlignmentCyclesDetector {
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


  private static class OffsetPair {
    private final static int MAX_VALUE = Integer.MAX_VALUE >>> 16;
    private final int first;
    private final int second;

    private OffsetPair(int first, int second) {
      this.first = first;
      this.second = second;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj instanceof OffsetPair) {
        OffsetPair other = (OffsetPair)obj;
        return other.first == first && other.second == second;
      }
      return false;
    }

    @Override
    public int hashCode() {
      int a = first >= MAX_VALUE ? first % MAX_VALUE : first;
      int b = second >= MAX_VALUE ? second % MAX_VALUE : second;
      int sum = a + b;
      if (sum >= MAX_VALUE) sum = sum % MAX_VALUE;
      return sum * (sum + 1) / 2 + a;
    }

    @Override
    public String toString() {
      return "(" + first + ", " + second + ")";
    }
  }

}
