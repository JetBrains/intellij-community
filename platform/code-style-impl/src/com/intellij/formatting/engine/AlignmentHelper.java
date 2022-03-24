// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting.engine;

import com.intellij.diagnostic.AttachmentFactory;
import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.util.containers.MultiMap;

import java.util.*;

public class AlignmentHelper {
  private static final Logger LOG = Logger.getInstance(AlignmentHelper.class);

  private static final Map<Alignment.Anchor, BlockAlignmentProcessor> ALIGNMENT_PROCESSORS = new EnumMap<>(Alignment.Anchor.class);
  static {
    ALIGNMENT_PROCESSORS.put(Alignment.Anchor.LEFT, new LeftEdgeAlignmentProcessor());
    ALIGNMENT_PROCESSORS.put(Alignment.Anchor.RIGHT, new RightEdgeAlignmentProcessor());
  }

  /**
   * There is a possible case that we detect a 'cycled alignment' rules (see {@link #myBackwardShiftedAlignedBlocks}). We want
   * just to skip processing for such alignments then.
   * <p/>
   * This container holds 'bad alignment' objects that should not be processed.
   */
  private final Set<Alignment> myAlignmentsToSkip = new HashSet<>();
  private final Document myDocument;
  private final BlockIndentOptions myBlockIndentOptions;

  private final AlignmentCyclesDetector myCyclesDetector;

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
  private final Map<LeafBlockWrapper, Set<LeafBlockWrapper>> myBackwardShiftedAlignedBlocks = new HashMap<>();
  private final Map<AbstractBlockWrapper, Set<AbstractBlockWrapper>> myAlignmentMappings = new HashMap<>();

  public AlignmentHelper(Document document, MultiMap<Alignment, Block> blocksToAlign, BlockIndentOptions options) {
    myDocument = document;
    myBlockIndentOptions = options;
    int totalBlocks = blocksToAlign.values().size();
    myCyclesDetector = new AlignmentCyclesDetector(totalBlocks);
  }

  private static void reportAlignmentProcessingError(BlockAlignmentProcessor.Context context) {
    ASTNode node = context.targetBlock.getNode();
    Language language = node != null ? node.getPsi().getLanguage() : null;
    String message = (language != null ? language.getDisplayName() + ": " : "") + "Can't align block " + context.targetBlock;
    LOG.error(message, new Throwable(), AttachmentFactory.createAttachment(context.document));
  }

  LeafBlockWrapper applyAlignment(final AlignmentImpl alignment, final LeafBlockWrapper currentBlock) {
    BlockAlignmentProcessor alignmentProcessor = ALIGNMENT_PROCESSORS.get(alignment.getAnchor());
    if (alignmentProcessor == null) {
      LOG.error(String.format("Can't find alignment processor for alignment anchor %s", alignment.getAnchor()));
      return null;
    }

    BlockAlignmentProcessor.Context context = new BlockAlignmentProcessor.Context(
      myDocument, alignment, currentBlock, myAlignmentMappings, myBackwardShiftedAlignedBlocks,
      myBlockIndentOptions.getIndentOptions(currentBlock));
    final LeafBlockWrapper offsetResponsibleBlock = alignment.getOffsetRespBlockBefore(currentBlock);
    if (offsetResponsibleBlock != null) {
      myCyclesDetector.registerOffsetResponsibleBlock(offsetResponsibleBlock);
    }
    BlockAlignmentProcessor.Result result = alignmentProcessor.applyAlignment(context);
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
        Set<LeafBlockWrapper> blocksCausedRealignment = new HashSet<>();
        myBackwardShiftedAlignedBlocks.clear();
        myBackwardShiftedAlignedBlocks.put(offsetResponsibleBlock, blocksCausedRealignment);
        blocksCausedRealignment.add(currentBlock);
        storeAlignmentMapping(currentBlock, offsetResponsibleBlock);
        if (myCyclesDetector.isCycleDetected()) {
          reportAlignmentProcessingError(context);
          return null;
        }
        myCyclesDetector.registerBlockRollback(currentBlock);
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

  boolean shouldSkip(AlignmentImpl alignment) {
    return myAlignmentsToSkip.contains(alignment);
  }

  private void storeAlignmentMapping(AbstractBlockWrapper block1, AbstractBlockWrapper block2) {
    doStoreAlignmentMapping(block1, block2);
    doStoreAlignmentMapping(block2, block1);
  }

  private void doStoreAlignmentMapping(AbstractBlockWrapper key, AbstractBlockWrapper value) {
    Set<AbstractBlockWrapper> wrappers = myAlignmentMappings.get(key);
    if (wrappers == null) {
      myAlignmentMappings.put(key, wrappers = new HashSet<>());
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

}
