// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting;

import com.intellij.formatting.engine.State;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.formatter.common.ExtraRangesProvider;
import com.intellij.util.containers.Stack;
import one.util.streamex.StreamEx;

import java.util.ArrayList;
import java.util.List;

final class AdjustFormatRangesState extends State {
  private static final RangesAssert ASSERT = new RangesAssert();

  private final FormatTextRanges        myFormatRanges;
  private final List<TextRange>         myExtendedRanges;
  private final List<TextRange>         totalNewRanges = new ArrayList<>();
  private final Stack<Block>            state;
  private final FormattingDocumentModel myModel;

  AdjustFormatRangesState(Block currentRoot, FormatTextRanges formatRanges, FormattingDocumentModel model) {
    myModel = model;
    myFormatRanges = formatRanges;
    myExtendedRanges = formatRanges.getExtendedRanges();
    state = new Stack<>(currentRoot);
    setOnDone(() -> totalNewRanges.forEach(range -> myFormatRanges.add(range, false)));
  }

  @Override
  public void doIteration() {
    Block currentBlock = state.pop();
    processBlock(currentBlock);
    setDone(state.isEmpty());
  }

  private void processBlock(Block currentBlock) {
    if (!isInsideExtendedFormattingRanges(currentBlock)) return;

    StreamEx.ofReversed(currentBlock.getSubBlocks())
      .filter(block -> ASSERT.checkChildRange(currentBlock.getTextRange(), block.getTextRange(), myModel))
      .forEach(block -> state.push(block));

    if (!myFormatRanges.isReadOnly(currentBlock.getTextRange())) {
      extractRanges(currentBlock);
    }
  }

  private boolean isInsideExtendedFormattingRanges(Block currentBlock) {
    TextRange blockRange = currentBlock.getTextRange();
    return myExtendedRanges.stream().anyMatch(range -> range.intersects(blockRange));
  }

  private void extractRanges(Block block) {
    if (block instanceof ExtraRangesProvider) {
      List<TextRange> newRanges = ((ExtraRangesProvider)block).getExtraRangesToFormat(myFormatRanges);
      if (newRanges != null) {
        totalNewRanges.addAll(newRanges);
      }
    }
  }
}
