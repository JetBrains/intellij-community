// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting;

import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

final class ProbablyIncreasingLowerboundAlgorithm<T extends AbstractBlockWrapper> {
  private List<T> myBlocks;

  private int myLastCalculatedOffset = -1;
  private int myLastCalculatedAnswerIndex = -1;

  ProbablyIncreasingLowerboundAlgorithm(@NotNull List<T> blocks) {
    myBlocks = blocks;
  }

  public void reset() {
    myLastCalculatedAnswerIndex = -1;
    myLastCalculatedOffset = -1;
  }

  public List<T> getLeftSubList(@Nullable AbstractBlockWrapper block) {
    return myBlocks.subList(0, getLeftRespNeighborIndex(block) + 1);
  }

  public void setBlocksList(List<T> blocks) {
    myBlocks = blocks;
    reset();
  }

  public @Nullable AbstractBlockWrapper getLeftRespNeighbor(@NotNull AbstractBlockWrapper block) {
    int index = getLeftRespNeighborIndex(block);
    if (index == -1) {
      return null;
    }
    else {
      return myBlocks.get(index);
    }
  }

  private int getLeftRespNeighborIndex(@Nullable AbstractBlockWrapper block) {
    if (block == null) {
      return myBlocks.size() - 1;
    }

    final int offset = block.getStartOffset();
    if (myLastCalculatedOffset != -1) {
      if (offset >= myLastCalculatedOffset) {
        myLastCalculatedAnswerIndex = calcLeftRespNeighborIndexLinear(myLastCalculatedAnswerIndex, offset);
        myLastCalculatedOffset = offset;
        return myLastCalculatedAnswerIndex;
      }

      //Logger.getInstance(ProbablyIncreasingLowerboundAlgorithm.class).error("not very good!");
      myLastCalculatedOffset = -1;
    }
    myLastCalculatedOffset = offset;
    return myLastCalculatedAnswerIndex = calcLeftRespNeighborIndex(offset);
  }

  private int calcLeftRespNeighborIndexLinear(int lastAnswerIndex, int blockOffset) {
    int index = lastAnswerIndex;
    while (index + 1 < myBlocks.size()) {
      if (blockOffset <= myBlocks.get(index + 1).getStartOffset()) {
        break;
      }
      index++;
    }
    return index;
  }

  private int calcLeftRespNeighborIndex(final int blockOffset) {
    int i = ObjectUtils.binarySearch(0,myBlocks.size(),m-> myBlocks.get(m).getStartOffset() < blockOffset ? -1 : 1);
    return -i - 2;
  }
}
