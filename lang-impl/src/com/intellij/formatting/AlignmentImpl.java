package com.intellij.formatting;

import java.util.*;

class AlignmentImpl extends Alignment {
  private static final List<LeafBlockWrapper> EMPTY = Collections.unmodifiableList(new ArrayList<LeafBlockWrapper>(0));
  private Collection<LeafBlockWrapper> myOffsetRespBlocks = EMPTY;
  private final int myFlags;
  private static int ourId = 0;
  private static final int ID_SHIFT = 1;
  private AlignmentImpl myParentAlignment;

  public String getId() {
    return String.valueOf(myFlags >>> ID_SHIFT);
  }

  public void reset() {
    if (myOffsetRespBlocks != EMPTY) myOffsetRespBlocks.clear();
  }

  public void setParent(final Alignment base) {
    myParentAlignment = (AlignmentImpl)base;
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

  LeafBlockWrapper getOffsetRespBlockBefore(final LeafBlockWrapper block) {
    LeafBlockWrapper result = null;
    if (myOffsetRespBlocks != EMPTY) {
      LeafBlockWrapper lastBlockAfterLineFeed = null;
      LeafBlockWrapper firstAlignedBlock = null;
      LeafBlockWrapper lastAlignedBlock = null;
      for (Iterator<LeafBlockWrapper> each = myOffsetRespBlocks.iterator(); each.hasNext();) {
        final LeafBlockWrapper current = each.next();
        if (block == null || current.getStartOffset() < block.getStartOffset()) {
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
      if (lastBlockAfterLineFeed != null) {
        result = lastBlockAfterLineFeed;
      }
      else if (firstAlignedBlock != null) {
        result = firstAlignedBlock;
      }
      else {
        result = lastAlignedBlock;
      }
    }
    if (result == null && myParentAlignment != null) {
      return myParentAlignment.getOffsetRespBlockBefore(block);
    }
    else {
      return result;
    }

  }

  void setOffsetRespBlock(final LeafBlockWrapper block) {
    if (myOffsetRespBlocks == EMPTY) myOffsetRespBlocks = new LinkedHashSet<LeafBlockWrapper>(1);
    myOffsetRespBlocks.add(block);
  }

}
