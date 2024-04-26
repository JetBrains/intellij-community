// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting.engine;

import com.intellij.formatting.*;
import com.intellij.psi.codeStyle.CodeStyleConstraints;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class IndentAdjuster {
  private final AlignmentHelper myAlignmentHelper;
  private final BlockIndentOptions myBlockIndentOptions;

  public IndentAdjuster(BlockIndentOptions blockIndentOptions, AlignmentHelper alignmentHelper) {
    myAlignmentHelper = alignmentHelper;
    myBlockIndentOptions = blockIndentOptions;
  }

  /**
   * Sometimes to align block we adjust whitespace of other block before.
   * In such a case, we rollback to that block restarting formatting from there.
   * 
   * @return new current block if we need to rollback, null otherwise
   */
  LeafBlockWrapper adjustIndent(LeafBlockWrapper block) {
    AlignmentImpl alignment = CoreFormatterUtil.getAlignment(block);
    WhiteSpace whiteSpace = block.getWhiteSpace();

    if (alignment == null || myAlignmentHelper.shouldSkip(alignment)) {
      if (whiteSpace.containsLineFeeds()) {
        adjustSpacingByIndentOffset(block);
      }
      else {
        whiteSpace.arrangeSpaces(block.getSpaceProperty());
      }
      return null;
    }

    return myAlignmentHelper.applyAlignment(alignment, block);
  }

  private void adjustSpacingByIndentOffset(LeafBlockWrapper block) {
    CommonCodeStyleSettings.IndentOptions options = myBlockIndentOptions.getIndentOptions(block);
    IndentData offset = block.calculateOffset(options);
    block.getWhiteSpace().setSpaces(offset.getSpaces(), offset.getIndentSpaces());
  }
  
  public void adjustLineIndent(LeafBlockWrapper myCurrentBlock) {
    IndentData alignOffset = getAlignOffset(myCurrentBlock);

    if (alignOffset == null) {
      adjustSpacingByIndentOffset(myCurrentBlock);
    }
    else {
      myCurrentBlock.getWhiteSpace().setSpaces(alignOffset.getSpaces(), alignOffset.getIndentSpaces());
    }
  }
  
  private static @Nullable IndentData getAlignOffset(LeafBlockWrapper myCurrentBlock) {
    AbstractBlockWrapper current = myCurrentBlock;
    while (true) {
      final AlignmentImpl alignment = current.getAlignment();
      LeafBlockWrapper offsetResponsibleBlock;
      if (alignment != null && (offsetResponsibleBlock = alignment.getOffsetRespBlockBefore(myCurrentBlock)) != null) {
        final WhiteSpace whiteSpace = offsetResponsibleBlock.getWhiteSpace();
        if (whiteSpace.containsLineFeeds()) {
          return new IndentData(whiteSpace.getIndentSpaces(), whiteSpace.getSpaces());
        }
        else {
          final int offsetBeforeBlock = CoreFormatterUtil.getStartColumn(offsetResponsibleBlock);
          final AbstractBlockWrapper indentedParentBlock = CoreFormatterUtil.getIndentedParentBlock(myCurrentBlock);
          if (indentedParentBlock == null) {
            return new IndentData(0, offsetBeforeBlock);
          }
          else {
            final int parentIndent = indentedParentBlock.getWhiteSpace().getIndentOffset();
            if (parentIndent > offsetBeforeBlock) {
              return new IndentData(0, offsetBeforeBlock);
            }
            else {
              return new IndentData(parentIndent, offsetBeforeBlock - parentIndent);
            }
          }
        }
      }
      else {
        current = current.getParent();
        if (current == null || current.getStartOffset() != myCurrentBlock.getStartOffset()) return null;
      }
    }
  }

  public IndentInfo adjustLineIndent(FormatProcessor.ChildAttributesInfo info, AbstractBlockWrapper child) {
    AbstractBlockWrapper parent = info.parent();
    ChildAttributes childAttributes = info.attributes();
    int index = info.index();

    AlignWhiteSpace alignWhiteSpace = getAlignOffsetBefore(childAttributes.getAlignment());
    if (alignWhiteSpace == null) {
      return parent.calculateChildOffset(myBlockIndentOptions.getIndentOptions(child), childAttributes, index).createIndentInfo();
    }
    else {
      return new IndentInfo(0, alignWhiteSpace.indentSpaces, alignWhiteSpace.alignSpaces);
    }
  }

  private static final class AlignWhiteSpace {
    int indentSpaces = 0;
    int alignSpaces = 0;
  }

  private static AlignWhiteSpace getAlignOffsetBefore(final @Nullable Alignment alignment) {
    if (alignment == null) return null;
    final LeafBlockWrapper alignRespBlock = ((AlignmentImpl)alignment).getOffsetRespBlockBefore(null);
    return alignRespBlock != null ? getStartColumn(alignRespBlock) : null;
  }

  private static AlignWhiteSpace getStartColumn(@NotNull LeafBlockWrapper block) {
    AlignWhiteSpace alignWhitespace = new AlignWhiteSpace();
    while (true) {
      final WhiteSpace whiteSpace = block.getWhiteSpace();
      alignWhitespace.alignSpaces += whiteSpace.getSpaces();

      if (whiteSpace.containsLineFeeds()) {
        alignWhitespace.indentSpaces += whiteSpace.getIndentSpaces();
        return alignWhitespace;
      }
      else {
        alignWhitespace.alignSpaces += whiteSpace.getIndentSpaces();
      }

      block = block.getPreviousBlock();
      if (alignWhitespace.alignSpaces > CodeStyleConstraints.MAX_RIGHT_MARGIN || block == null) return alignWhitespace;
      alignWhitespace.alignSpaces += block.getSymbolsAtTheLastLine();
      if (block.containsLineFeeds()) return alignWhitespace;
    }
  }

}
