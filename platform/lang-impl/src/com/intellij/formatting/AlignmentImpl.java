/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.formatting;

import org.jetbrains.annotations.Nullable;

import java.util.*;

class AlignmentImpl extends Alignment {
  private static final List<LeafBlockWrapper> EMPTY = Collections.unmodifiableList(new ArrayList<LeafBlockWrapper>(0));
  private Collection<LeafBlockWrapper> myOffsetRespBlocks = EMPTY;
  private AlignmentImpl myParentAlignment;

  public String getId() {
    return String.valueOf(System.identityHashCode(this));
  }

  public void reset() {
    if (myOffsetRespBlocks != EMPTY) myOffsetRespBlocks.clear();
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
   *            Use last block (block which has the greatest start offset) after the block which
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
   *                  {@link #setParent(Alignment) its parent} using the algorithm above if any; <code>null</code> otherwise
   */
  @Nullable
  LeafBlockWrapper getOffsetRespBlockBefore(final AbstractBlockWrapper block) {
    LeafBlockWrapper result = null;
    if (myOffsetRespBlocks != EMPTY) {
      LeafBlockWrapper lastBlockAfterLineFeed = null;
      LeafBlockWrapper firstAlignedBlock = null;
      LeafBlockWrapper lastAlignedBlock = null;
      for (final LeafBlockWrapper current : myOffsetRespBlocks) {
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

  /**
   * Registers wrapped block within the current alignment in order to use it for further
   * {@link #getOffsetRespBlockBefore(AbstractBlockWrapper)} calls processing.
   *
   * @param block   wrapped block to register within the current alignment object
   */
  void setOffsetRespBlock(final LeafBlockWrapper block) {
    if (myOffsetRespBlocks == EMPTY) myOffsetRespBlocks = new LinkedHashSet<LeafBlockWrapper>(1);
    myOffsetRespBlocks.add(block);
  }

}
