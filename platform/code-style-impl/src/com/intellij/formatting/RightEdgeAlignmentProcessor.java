// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting;

import org.jetbrains.annotations.NotNull;

/**
 * {@link BlockAlignmentProcessor} implementation for {@link Alignment} that
 * {@link Alignment.Anchor#RIGHT anchors to the right block edge}.
 */
public final class RightEdgeAlignmentProcessor extends AbstractBlockAlignmentProcessor {

  @Override
  protected IndentData calculateAlignmentAnchorIndent(@NotNull Context context) {
    LeafBlockWrapper offsetResponsibleBlock = context.alignment().getOffsetRespBlockBefore(context.targetBlock());
    if (offsetResponsibleBlock == null) {
      return null;
    }

    final WhiteSpace whiteSpace = offsetResponsibleBlock.getWhiteSpace();
    if (whiteSpace.containsLineFeeds()) {
      return new IndentData(whiteSpace.getIndentSpaces() + offsetResponsibleBlock.getSymbolsAtTheLastLine(), whiteSpace.getSpaces());
    }
    else {
      final int targetIndent = CoreFormatterUtil.getStartColumn(offsetResponsibleBlock)
                               + offsetResponsibleBlock.getSymbolsAtTheLastLine();
      final AbstractBlockWrapper prevIndentedBlock = CoreFormatterUtil.getIndentedParentBlock(context.targetBlock());
      if (prevIndentedBlock == null) {
        return new IndentData(0, targetIndent);
      }
      else {
        final int parentIndent = prevIndentedBlock.getWhiteSpace().getIndentOffset();
        return new IndentData(parentIndent, targetIndent - parentIndent);
      }
    }
  }

  @Override
  protected boolean applyIndentToTheFirstBlockOnLine(@NotNull IndentData alignmentAnchorIndent, @NotNull Context context) {
    WhiteSpace whiteSpace = context.targetBlock().getWhiteSpace();
    int indentSpaces = alignmentAnchorIndent.getIndentSpaces();
    int spaces = alignmentAnchorIndent.getSpaces() - context.targetBlock().getSymbolsAtTheLastLine();
    if (spaces < 0) {
      indentSpaces += spaces;
      spaces = 0;
    }

    if (indentSpaces >= 0) {
      whiteSpace.setSpaces(spaces, indentSpaces);
      return true;
    }
    if (whiteSpace.getTotalSpaces() > 0) {
      // The general idea is that there is a possible case that particular block that starts new line is much wider than its
      // preceding aligned block and its white space is not empty. We may move it to the left then trying to preserve its
      // indent.
      //
      // Example:
      // block11 block12
      //                          test-block-that-has-rather-big-width-and-aligned-to-block12
      // Let's say, the last block has indent '10', hence, the result would be:
      // block11   block12
      //           test-block-that-has-rather-big-width-and-aligned-to-block12
      CompositeBlockWrapper parent = context.targetBlock().getParent();
      if (parent != null) {
        IndentData childOffset = CoreFormatterUtil.getIndent(
          context.indentOptions(), context.targetBlock(), context.targetBlock().getStartOffset()
        );
        if (whiteSpace.getTotalSpaces() > childOffset.getTotalSpaces()) {
          int leftShift = whiteSpace.getTotalSpaces() - childOffset.getTotalSpaces();
          if (leftShift >= whiteSpace.getSpaces()) {
            spaces = 0;
            indentSpaces = whiteSpace.getIndentSpaces() - (leftShift - whiteSpace.getSpaces());
          }
          else {
            spaces = whiteSpace.getSpaces() - leftShift;
            indentSpaces = whiteSpace.getIndentSpaces();
          }
        }
        whiteSpace.setSpaces(spaces, indentSpaces);
      }
    }
    return false;
  }

  @Override
  protected int getAlignmentIndentDiff(@NotNull IndentData alignmentAnchorIndent, @NotNull Context context) {
    IndentData indentBeforeBlock = context.targetBlock().getNumberOfSymbolsBeforeBlock();
    int numberOfSymbolsBeforeBlock = indentBeforeBlock.getTotalSpaces() + context.targetBlock().getSymbolsAtTheLastLine();
    return alignmentAnchorIndent.getTotalSpaces() - numberOfSymbolsBeforeBlock;
  }
}
