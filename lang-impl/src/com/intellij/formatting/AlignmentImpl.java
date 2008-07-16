package com.intellij.formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Collections;

class AlignmentImpl extends Alignment {
  private static final List<LeafBlockWrapper> EMPTY = Collections.unmodifiableList(new ArrayList<LeafBlockWrapper>(0));
  private List<LeafBlockWrapper> myOffsetRespBlocks = EMPTY;
  private final int myFlags;
  private static int ourId = 0;
  private static final int ID_SHIFT = 1;

  public String getId() {
    return String.valueOf(myFlags >>> ID_SHIFT);
  }

  public void reset() {
    if (myOffsetRespBlocks != EMPTY) myOffsetRespBlocks.clear();
  }

  static enum Type{
    FULL,NORMAL
  }

  public AlignmentImpl(final Type type) {
    myFlags = ((ourId++) >> ID_SHIFT) | type.ordinal();
  }

  final Type getType() {
    return Type.values()[myFlags & 1];
  }

  LeafBlockWrapper getOffsetRespBlockBefore(final LeafBlockWrapper blockAfter) {
    if (myOffsetRespBlocks != EMPTY) {
      LeafBlockWrapper lastBlockAfterLineFeed = null;
      LeafBlockWrapper firstAlignedBlock = null;
      LeafBlockWrapper lastAlignedBlock = null;
      for (ListIterator<LeafBlockWrapper> each = myOffsetRespBlocks.listIterator(myOffsetRespBlocks.size()); each.hasPrevious();) {
        final LeafBlockWrapper current = each.previous();
        if (blockAfter == null || current.getStartOffset() < blockAfter.getStartOffset()) {
          if (firstAlignedBlock == null || firstAlignedBlock.getStartOffset() > current.getStartOffset()) {
            firstAlignedBlock = current;
          }

          if (lastAlignedBlock == null || lastAlignedBlock.getStartOffset() < current.getStartOffset()) {
            lastAlignedBlock = current;
          }

          if (current.getWhiteSpace().containsLineFeeds() &&
              (lastBlockAfterLineFeed == null || lastBlockAfterLineFeed.getStartOffset() < current.getStartOffset())) {
            lastBlockAfterLineFeed = current;
          }

        }
        //each.remove();
      }
      if (lastBlockAfterLineFeed != null) return lastBlockAfterLineFeed;
      if (firstAlignedBlock != null) return firstAlignedBlock;
      return lastAlignedBlock;
    }
    return null;
  }

  void setOffsetRespBlock(final LeafBlockWrapper block) {
    if (myOffsetRespBlocks == EMPTY) myOffsetRespBlocks = new ArrayList<LeafBlockWrapper>(1);
    myOffsetRespBlocks.add(block);
  }

}
