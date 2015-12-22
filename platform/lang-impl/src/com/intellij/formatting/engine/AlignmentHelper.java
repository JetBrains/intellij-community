/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.formatting.engine;

import com.intellij.diagnostic.LogMessageEx;
import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class AlignmentHelper {
  private static final Logger LOG = Logger.getInstance(AlignmentHelper.class);

  private static final Map<Alignment.Anchor, BlockAlignmentProcessor> ALIGNMENT_PROCESSORS = new EnumMap<Alignment.Anchor, BlockAlignmentProcessor>(Alignment.Anchor.class);
  static {
    ALIGNMENT_PROCESSORS.put(Alignment.Anchor.LEFT, new LeftEdgeAlignmentProcessor());
    ALIGNMENT_PROCESSORS.put(Alignment.Anchor.RIGHT, new RightEdgeAlignmentProcessor());
  }

  private final Set<Alignment> myAlignmentsToSkip = ContainerUtil.newHashSet();
  private final Document myDocument;
  private final BlockIndentOptions myBlockIndentOptions;

  private int myTotalBlocksWithAlignments;
  private int myBlockRollbacks;


  /**
   * Remembers mappings between backward-shifted aligned block and blocks that cause that shift in order to detect
   * infinite cycles that may occur when, for example following alignment is specified:
   * <p/>
   * <pre>
   *     int i1     = 1;
   *     int i2, i3 = 2;
   * </pre>
   * <p/>
   * There is a possible case that <code>'i1'</code>, <code>'i2'</code> and <code>'i3'</code> blocks re-use
   * the same alignment, hence, <code>'i1'</code> is shifted to right during <code>'i3'</code> processing but
   * that causes <code>'i2'</code> to be shifted right as wll because it's aligned to <code>'i1'</code> that
   * increases offset of <code>'i3'</code> that, in turn, causes backward shift of <code>'i1'</code> etc.
   * <p/>
   * This map remembers such backward shifts in order to be able to break such infinite cycles.
   */
  private final Map<LeafBlockWrapper, Set<LeafBlockWrapper>> myBackwardShiftedAlignedBlocks = ContainerUtil.newHashMap();
  private final Map<AbstractBlockWrapper, Set<AbstractBlockWrapper>> myAlignmentMappings = ContainerUtil.newHashMap();

  public AlignmentHelper(Document document, MultiMap<Alignment, Block> blocksToAlign, BlockIndentOptions options) {
    myDocument = document;
    myTotalBlocksWithAlignments = blocksToAlign.values().size();
    myBlockIndentOptions = options;
  }
  
  private static void reportAlignmentProcessingError(BlockAlignmentProcessor.Context context) {
    ASTNode node = context.targetBlock.getNode();
    Language language = node != null ? node.getPsi().getLanguage() : null;
    LogMessageEx.error(LOG,
                       (language != null ? language.getDisplayName() + ": " : "") +
                       "Can't align block " + context.targetBlock, context.document.getText());
  }

  public LeafBlockWrapper applyAlignment(final AlignmentImpl alignment, final LeafBlockWrapper currentBlock) {
    BlockAlignmentProcessor alignmentProcessor = ALIGNMENT_PROCESSORS.get(alignment.getAnchor());
    if (alignmentProcessor == null) {
      LOG.error(String.format("Can't find alignment processor for alignment anchor %s", alignment.getAnchor()));
      return null;
    }

    BlockAlignmentProcessor.Context context = new BlockAlignmentProcessor.Context(
      myDocument, alignment, currentBlock, myAlignmentMappings, myBackwardShiftedAlignedBlocks,
      myBlockIndentOptions.getIndentOptions(currentBlock));
    BlockAlignmentProcessor.Result result = alignmentProcessor.applyAlignment(context);
    final LeafBlockWrapper offsetResponsibleBlock = alignment.getOffsetRespBlockBefore(currentBlock);
    switch (result) {
      case TARGET_BLOCK_PROCESSED_NOT_ALIGNED:
        return null;
      case TARGET_BLOCK_ALIGNED:
        storeAlignmentMapping(currentBlock);
        return null;
      case BACKWARD_BLOCK_ALIGNED:
        if (offsetResponsibleBlock == null) {
          return null;
        }
        Set<LeafBlockWrapper> blocksCausedRealignment = new HashSet<LeafBlockWrapper>();
        myBackwardShiftedAlignedBlocks.clear();
        myBackwardShiftedAlignedBlocks.put(offsetResponsibleBlock, blocksCausedRealignment);
        blocksCausedRealignment.add(currentBlock);
        storeAlignmentMapping(currentBlock, offsetResponsibleBlock);

        if (myBlockRollbacks > myTotalBlocksWithAlignments) {
          reportAlignmentProcessingError(context);
          return null;
        }
        myBlockRollbacks++;
        return offsetResponsibleBlock.getNextBlock();
      case RECURSION_DETECTED:
        myAlignmentsToSkip.add(alignment);
        return offsetResponsibleBlock; // Fall through to the 'register alignment to skip'.
      case UNABLE_TO_ALIGN_BACKWARD_BLOCK:
        myAlignmentsToSkip.add(alignment);
        return null;
      default:
        return null;
    }
  }

  public boolean shouldSkip(AlignmentImpl alignment) {
    return myAlignmentsToSkip.contains(alignment);
  }

  private void storeAlignmentMapping(AbstractBlockWrapper block1, AbstractBlockWrapper block2) {
    doStoreAlignmentMapping(block1, block2);
    doStoreAlignmentMapping(block2, block1);
  }

  private void doStoreAlignmentMapping(AbstractBlockWrapper key, AbstractBlockWrapper value) {
    Set<AbstractBlockWrapper> wrappers = myAlignmentMappings.get(key);
    if (wrappers == null) {
      myAlignmentMappings.put(key, wrappers = new HashSet<AbstractBlockWrapper>());
    }
    wrappers.add(value);
  }

  private void storeAlignmentMapping(LeafBlockWrapper currentBlock) {
    AlignmentImpl alignment = null;
    AbstractBlockWrapper block = currentBlock;
    while (alignment == null && block != null) {
      alignment = block.getAlignment();
      block = block.getParent();
    }
    if (alignment != null) {
      block = alignment.getOffsetRespBlockBefore(currentBlock);
      if (block != null) {
        storeAlignmentMapping(currentBlock, block);
      }
    }
  }

  public void reset() {
    myBackwardShiftedAlignedBlocks.clear();
    myAlignmentMappings.clear();
  }
}
