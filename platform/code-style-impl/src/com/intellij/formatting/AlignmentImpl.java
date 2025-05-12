// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.formatting;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.*;

@ApiStatus.Internal
public final class AlignmentImpl extends Alignment {
  private static final List<LeafBlockWrapper> EMPTY = Collections.emptyList();
  private final boolean myAllowBackwardShift;
  private final Anchor myAnchor;
  private List<LeafBlockWrapper> myOffsetRespBlocks = EMPTY;
  private AlignmentImpl myParentAlignment;
  private final ProbablyIncreasingLowerboundAlgorithm<LeafBlockWrapper> myOffsetRespBlocksCalculator;

  AlignmentImpl() {
    this(false, Anchor.LEFT);
  }

  @VisibleForTesting
  public AlignmentImpl(boolean allowBackwardShift, @NotNull Anchor anchor) {
    myAllowBackwardShift = allowBackwardShift;
    myAnchor = anchor;
    myOffsetRespBlocksCalculator = new ProbablyIncreasingLowerboundAlgorithm<>(myOffsetRespBlocks);
  }

  public boolean isAllowBackwardShift() {
    return myAllowBackwardShift;
  }

  public @NotNull Anchor getAnchor() {
    return myAnchor;
  }

  public String getId() {
    return String.valueOf(System.identityHashCode(this));
  }

  public void reset() {
    if (myOffsetRespBlocks != EMPTY) {
      myOffsetRespBlocks.clear();
    }
    myOffsetRespBlocksCalculator.reset();
  }

  public void setParent(final Alignment base) {
    myParentAlignment = (AlignmentImpl)base;
  }

  /**
   * Selects target wrapped block by the following algorithm:
   * <ol>
   *   <li>
   *      Filter blocks registered via {@link #setOffsetRespBlock(LeafBlockWrapper)} in order to process only those that start
   *      before the given block (blocks which start offset is lower than start offset of the given block).
   *   </li>
   *   <li>
   *      Try to find out result from those filtered blocks using the following algorithm:
   *      <ol>
   *        <li>
   *            Use the last block (block which has the greatest start offset) after the block which
   *            {@link AbstractBlockWrapper#getWhiteSpace() white space} contains line feeds;
   *        </li>
   *        <li>
   *            Use the first block (block with the smallest start offset) if no block can be selected using the rule above;
   *        </li>
   *        <li>
   *            Use the last block (block with the greatest start offset) if no block can be selected using the rules above;
   *        </li>
   *      </ol>
   *   </li>
   *   <li>
   *      Delegate the task to the {@link #setParent(Alignment) parent alignment} (if it's registered) if no blocks
   *      are configured for the current one;
   *   </li>
   * </ol>
   *
   * @param block     target block to use during blocks filtering
   * @return          block {@link #setOffsetRespBlock(LeafBlockWrapper) registered} for the current alignment object or
   *                  {@link #setParent(Alignment) its parent} using the algorithm above if any; {@code null} otherwise
   */
  public @Nullable LeafBlockWrapper getOffsetRespBlockBefore(final @Nullable AbstractBlockWrapper block) {
    if (!continueOffsetResponsibleBlockRetrieval(block)) {
      return null;
    }

    LeafBlockWrapper result = null;
    final List<LeafBlockWrapper> leftBlocks = myOffsetRespBlocksCalculator.getLeftSubList(block);
    if (!leftBlocks.isEmpty()) {
      result = leftBlocks.get(0);
    }

    if (result == null && myParentAlignment != null) {
      return myParentAlignment.getOffsetRespBlockBefore(block);
    }
    else {
      return result;
    }
  }

  /**
   * Registers wrapped block within the current alignment in order to use it for further
   * {@link #getOffsetRespBlockBefore(AbstractBlockWrapper)} calls processing.
   *
   * @param block   wrapped block to register within the current alignment object
   */
  public void setOffsetRespBlock(final LeafBlockWrapper block) {
    if (block == null) {
      return;
    }
    if (myOffsetRespBlocks == EMPTY) {
      myOffsetRespBlocks = new ArrayList<>(1);
      myOffsetRespBlocksCalculator.setBlocksList(myOffsetRespBlocks);
    }
    myOffsetRespBlocks.add(block);
  }
  
  public Set<LeafBlockWrapper> getOffsetResponsibleBlocks() {
    return new HashSet<>(myOffsetRespBlocks);
  }

  private static @NotNull AbstractBlockWrapper extendBlockFromStart(@NotNull AbstractBlockWrapper block) {
    while (true) {
      AbstractBlockWrapper parent = block.getParent();
      if (parent != null && parent.getStartOffset() == block.getStartOffset()) {
        block = parent;
      }
      else {
        return block;
      }
    }
  }

  private static @NotNull AbstractBlockWrapper extendBlockFromEnd(@NotNull AbstractBlockWrapper block) {
    while (true) {
      AbstractBlockWrapper parent = block.getParent();
      if (parent != null && parent.getEndOffset() == block.getEndOffset()) {
        block = parent;
      }
      else {
        return block;
      }
    }
  }

  private boolean continueOffsetResponsibleBlockRetrieval(@Nullable AbstractBlockWrapper block) {
    // We don't want to align block that doesn't start new line if it's not configured for 'by columns' alignment.
    if (!myAllowBackwardShift && block != null && !block.getWhiteSpace().containsLineFeeds()) {
      return false;
    }

    if (block != null) {
      AbstractBlockWrapper prevAlignBlock = myOffsetRespBlocksCalculator.getLeftRespNeighbor(block);
      if (!onDifferentLines(prevAlignBlock, block)) {
        return false;
      }

      //blocks are on different lines
      if (myAllowBackwardShift
          && myAnchor == Anchor.RIGHT
          && prevAlignBlock != null
          && prevAlignBlock.getWhiteSpace().containsLineFeeds() // {prevAlignBlock} starts new indent => can be moved
      ) {
        // extend block on position for right align
        prevAlignBlock = extendBlockFromStart(prevAlignBlock);

        AbstractBlockWrapper current = block;
        do {
          if (current.getStartOffset() < prevAlignBlock.getEndOffset()) {
            return false; //{prevAlignBlock{current}} | {current}{prevAlignBlock}, no new lines
          }
          if (current.getWhiteSpace().containsLineFeeds()) {
            break; // correct new line was found
          }
          else {
            AbstractBlockWrapper prev = current.getPreviousBlock();
            if (prev != null) {
                prev = extendBlockFromEnd(prev);
            }
            current = prev;
          }
        } while (current != null);
        if (current == null) {
          return false; //root block is the top
        }
      }
    }
    return myParentAlignment == null || myParentAlignment.continueOffsetResponsibleBlockRetrieval(block);
  }

  private static boolean onDifferentLines(AbstractBlockWrapper block1, AbstractBlockWrapper block2) {
    if (block1 == null || block2 == null) {
      return true;
    }

    AbstractBlockWrapper leftBlock = block1.getStartOffset() <= block2.getStartOffset() ? block1 : block2;
    AbstractBlockWrapper rightBlock = block1.getStartOffset() > block2.getStartOffset() ? block1 : block2;
    for (; rightBlock != null && rightBlock.getStartOffset() > leftBlock.getStartOffset(); rightBlock = rightBlock.getPreviousBlock()) {
      if (rightBlock.getWhiteSpace().containsLineFeeds()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public String toString() {
    return "Align: " + System.identityHashCode(this) + "," +  getAnchor() +  (isAllowBackwardShift() ? "<" : "");
  }
}
