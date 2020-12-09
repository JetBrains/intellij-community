// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting.engine;

import com.intellij.formatting.LeafBlockWrapper;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.codeStyle.CodeStyleConstraints;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;

public final class BlockRangesMap {
  private final LeafBlockWrapper myLastBlock;
  private final Int2ObjectMap<LeafBlockWrapper> myTextRangeToWrapper;

  public BlockRangesMap(LeafBlockWrapper first, LeafBlockWrapper last) {
    myLastBlock = last;
    myTextRangeToWrapper = buildTextRangeToInfoMap(first);
  }

  private static Int2ObjectMap<LeafBlockWrapper> buildTextRangeToInfoMap(final LeafBlockWrapper first) {
    final Int2ObjectMap<LeafBlockWrapper> result = new Int2ObjectOpenHashMap<>();
    LeafBlockWrapper current = first;
    while (current != null) {
      result.put(current.getStartOffset(), current);
      current = current.getNextBlock();
    }
    return result;
  }

  public boolean containsLineFeedsOrTooLong(final TextRange dependency) {
    LeafBlockWrapper child = myTextRangeToWrapper.get(dependency.getStartOffset());
    if (child == null) return false;
    final int endOffset = dependency.getEndOffset();
    final int startOffset = child.getStartOffset();
    while (child != null && child.getEndOffset() < endOffset) {
      if (child.containsLineFeeds() || (child.getStartOffset() - startOffset) > CodeStyleConstraints.MAX_RIGHT_MARGIN) return true;
      child = child.getNextBlock();
      if (child != null &&
          child.getWhiteSpace().getEndOffset() <= endOffset &&
          child.getWhiteSpace().containsLineFeeds()) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  public LeafBlockWrapper getBlockAtOrAfter(final int startOffset) {
    int current = startOffset;
    LeafBlockWrapper result = null;
    while (current < myLastBlock.getEndOffset()) {
      final LeafBlockWrapper currentValue = myTextRangeToWrapper.get(current);
      if (currentValue != null) {
        result = currentValue;
        break;
      }
      current++;
    }

    LeafBlockWrapper prevBlock = getPrevBlock(result);

    if (prevBlock != null && prevBlock.contains(startOffset)) {
      return prevBlock;
    }
    else {
      return result;
    }
  }

  @Nullable
  private LeafBlockWrapper getPrevBlock(@Nullable final LeafBlockWrapper result) {
    if (result != null) {
      return result.getPreviousBlock();
    }
    else {
      return myLastBlock;
    }
  }
}