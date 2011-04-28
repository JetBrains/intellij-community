/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * {@link BlockAlignmentProcessor} implementation for {@link Alignment} that
 * {@link Alignment.Anchor#LEFT anchors to the left block edge}.
 * 
 * @author Denis Zhdanov
 * @since 4/28/11 4:03 PM
 */
public class LeftEdgeAlignmentProcessor implements BlockAlignmentProcessor {

  private static final Logger LOG = Logger.getInstance("#" + LeftEdgeAlignmentProcessor.class.getName());
  
  @Override
  public Result applyAlignment(@NotNull Context context) {
    IndentData indent = calculateAlignIndent(context.alignment, context.targetBlock);
    if (indent == null) {
      return Result.TARGET_BLOCK_PROCESSED_NOT_ALIGNED;
    }
    WhiteSpace whiteSpace = context.targetBlock.getWhiteSpace();
    if (whiteSpace.containsLineFeeds()) {
      whiteSpace.setSpaces(indent.getSpaces(), indent.getIndentSpaces());
      return Result.TARGET_BLOCK_ALIGNED;
    }

    IndentData indentBeforeBlock = context.targetBlock.getNumberOfSymbolsBeforeBlock();
    int diff = indent.getTotalSpaces() - indentBeforeBlock.getTotalSpaces();
    if (diff == 0) {
      return Result.TARGET_BLOCK_ALIGNED;
    }

    if (diff > 0) {
      whiteSpace.setSpaces(whiteSpace.getSpaces() + diff, whiteSpace.getIndentSpaces());

      // Avoid tabulations usage for aligning blocks that are not the first blocks on a line.
      if (!whiteSpace.containsLineFeeds()) {
        whiteSpace.setForceSkipTabulationsUsage(true);
      }
      
      return Result.TARGET_BLOCK_ALIGNED;
    }

    if (!context.alignment.isAllowBackwardShift()) {
      return Result.TARGET_BLOCK_PROCESSED_NOT_ALIGNED;
    }

    LeafBlockWrapper offsetResponsibleBlock = context.alignment.getOffsetRespBlockBefore(context.targetBlock);
    if (offsetResponsibleBlock == null) {
      return Result.TARGET_BLOCK_PROCESSED_NOT_ALIGNED;
    }
    context.backwardAlignmentTarget = offsetResponsibleBlock;
    
    if (offsetResponsibleBlock.getWhiteSpace().isIsReadOnly()) {
      // We're unable to perform backward shift because white space for the target element is read-only.
      return Result.UNABLE_TO_ALIGN_BACKWARD_BLOCK;
    }

    if (!allowBackwardAlignment(context)) {
      return Result.UNABLE_TO_ALIGN_BACKWARD_BLOCK;
    }

    // There is a possible case that alignment options are defined incorrectly. Consider the following example:
    //     int i1;
    //     int i2, i3;
    // There is a problem if all blocks above use the same alignment - block 'i1' is shifted to right in order to align
    // to block 'i3' and reformatting starts back after 'i1'. Now 'i2' is shifted to left as well in order to align to the
    // new 'i1' position. That changes 'i3' position as well that causes 'i1' to be shifted right one more time.
    // Hence, we have endless cycle here. We remember information about blocks that caused indentation change because of
    // alignment of blocks located before them and post error every time we detect endless cycle.
    Set<LeafBlockWrapper> blocksCausedRealignment = context.backwardShiftedAlignedBlocks.get(offsetResponsibleBlock);
    if (blocksCausedRealignment != null && blocksCausedRealignment.contains(context.targetBlock)) {
      LOG.error(String.format("Formatting error - code block %s is set to be shifted right because of its alignment with "
                              + "block %s more than once. I.e. moving the former block because of alignment algorithm causes "
                              + "subsequent block to be shifted right as well - cyclic dependency",
                              offsetResponsibleBlock.getTextRange(), context.targetBlock.getTextRange()));
      blocksCausedRealignment.add(context.targetBlock);
      return Result.UNABLE_TO_ALIGN_BACKWARD_BLOCK;
    }
    
    WhiteSpace previousWhiteSpace = offsetResponsibleBlock.getWhiteSpace();
    previousWhiteSpace.setSpaces(previousWhiteSpace.getSpaces() - diff, previousWhiteSpace.getIndentOffset());
    // Avoid tabulations usage for aligning blocks that are not the first blocks on a line.
    if (!previousWhiteSpace.containsLineFeeds()) {
      previousWhiteSpace.setForceSkipTabulationsUsage(true);
    }

    return Result.BACKWARD_BLOCK_ALIGNED;
  }

  /**
   * Asks to calculate indent to be used within the white space of the given block.
   * 
   * @param alignment     target block alignment
   * @param targetBlock   target block
   * @return              indent to use for the white space of the given block
   */
  @Nullable
  private static IndentData calculateAlignIndent(@NotNull AlignmentImpl alignment, @NotNull LeafBlockWrapper targetBlock) {
    LeafBlockWrapper offsetResponsibleBlock = alignment.getOffsetRespBlockBefore(targetBlock);
    if (offsetResponsibleBlock == null) {
      return null;
    }
    
    final WhiteSpace whiteSpace = offsetResponsibleBlock.getWhiteSpace();
    if (whiteSpace.containsLineFeeds()) {
      return new IndentData(whiteSpace.getIndentSpaces(), whiteSpace.getSpaces());
    }
    else {
      final int offsetBeforeBlock = CoreFormatterUtil.getOffsetBefore(offsetResponsibleBlock);
      final AbstractBlockWrapper prevIndentedBlock = CoreFormatterUtil.getPreviousIndentedBlock(targetBlock);
      if (prevIndentedBlock == null) {
        return new IndentData(0, offsetBeforeBlock);
      }
      else {
        final int parentIndent = prevIndentedBlock.getWhiteSpace().getIndentOffset();
        if (parentIndent > offsetBeforeBlock) {
          return new IndentData(0, offsetBeforeBlock);
        }
        else {
          return new IndentData(parentIndent, offsetBeforeBlock - parentIndent);
        }
      }
    }
  }

  /**
   * It's possible to configure alignment in a way to allow
   * {@link AlignmentFactory#createAlignment(boolean, Alignment.Anchor)}  backward shift}.
   * <p/>
   * <b>Example:</b>
   * <pre>
   *     class Test {
   *         int i;
   *         StringBuilder buffer;
   *     }
   * </pre>
   * <p/>
   * It's possible that blocks <code>'i'</code> and <code>'buffer'</code> should be aligned. As formatter processes document from
   * start to end that means that requirement to shift block <code>'i'</code> to the right is discovered only during
   * <code>'buffer'</code> block processing. I.e. formatter returns to the previously processed block (<code>'i'</code>), modifies
   * its white space and continues from that location (performs 'backward' shift).
   * <p/>
   * Here is one very important moment - there is a possible case that formatting blocks are configured in a way that they are
   * combined in explicit cyclic graph.
   * <p/>
   * <b>Example:</b>
   * <pre>
   *     blah(bleh(blih,
   *       bloh), bluh);
   * </pre>
   * <p/>
   * Consider that pairs of blocks <code>'blih'; 'bloh'</code> and <code>'bleh', 'bluh'</code> should be aligned
   * and backward shift is possible for them. Here is how formatter works:
   * <ol>
   *   <li>
   *      Processing reaches <b>'bloh'</b> block. It's aligned to <code>'blih'</code> block. Current document state:
   *      <p/>
   *      <pre>
   *          blah(bleh(blih,
   *                    bloh), bluh);
   *      </pre>
   *   </li>
   *   <li>
   *      Processing reaches <b>'bluh'</b> block. It's aligned to <code>'blih'</code> block and backward shift is allowed, hence,
   *      <code>'blih'</code> block is moved to the right and processing contnues from it. Current document state:
   *      <pre>
   *          blah(            bleh(blih,
   *                    bloh), bluh);
   *      </pre>
   *   </li>
   *   <li>
   *      Processing reaches <b>'bloh'</b> block. It's configured to be aligned to <code>'blih'</code> block, hence, it's moved
   *      to the right:
   *      <pre>
   *          blah(            bleh(blih,
   *                                bloh), bluh);
   *      </pre>
   *   </li>
   *   <li>We have endless loop then;</li>
   * </ol>
   * So, that implies that we can't use backward alignment if the blocks are configured in a way that backward alignment
   * appliance produces endless loop. This method encapsulates the logic for checking if backward alignment can be applied.
   *
   * @param context           alignment processing context
   * @return                  <code>true</code> if backward alignment is possible; <code>false</code> otherwise
   */
  private static boolean allowBackwardAlignment(@NotNull Context context) {
    LeafBlockWrapper backwardTarget = context.backwardAlignmentTarget;
    if (backwardTarget == null) {
      return false;
    }
    Set<AbstractBlockWrapper> blocksBeforeCurrent = new HashSet<AbstractBlockWrapper>();
    for (
      LeafBlockWrapper previousBlock = context.targetBlock.getPreviousBlock();
      previousBlock != null;
      previousBlock = previousBlock.getPreviousBlock())
    {
      Set<AbstractBlockWrapper> blocks = context.alignmentMappings.get(previousBlock);
      if (blocks != null) {
        blocksBeforeCurrent.addAll(blocks);
      }

      if (previousBlock.getWhiteSpace().containsLineFeeds()) {
        break;
      }
    }

    for (
      LeafBlockWrapper next = backwardTarget.getNextBlock();
      next != null && !next.getWhiteSpace().containsLineFeeds();
      next = next.getNextBlock())
    {
      if (blocksBeforeCurrent.contains(next)) {
        return false;
      }
    }
    return true;
  }

}
