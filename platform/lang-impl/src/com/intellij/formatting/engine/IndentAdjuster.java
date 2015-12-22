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

import com.intellij.formatting.*;
import com.intellij.formatting.FormatProcessor;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import org.jetbrains.annotations.Nullable;

public class IndentAdjuster {
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
  public LeafBlockWrapper adjustIndent(LeafBlockWrapper block) {
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
  
  @Nullable
  private static IndentData getAlignOffset(LeafBlockWrapper myCurrentBlock) {
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

  public IndentInfo adjustLineIndent(LeafBlockWrapper currentBlock, FormatProcessor.ChildAttributesInfo info) {
    AbstractBlockWrapper parent = info.parent;
    ChildAttributes childAttributes = info.attributes;
    int index = info.index;

    int alignOffset = getAlignOffsetBefore(childAttributes.getAlignment(), null);
    if (alignOffset == -1) {
      return parent.calculateChildOffset(myBlockIndentOptions.getIndentOptions(parent), childAttributes, index).createIndentInfo();
    }
    else {
      AbstractBlockWrapper indentedParentBlock = CoreFormatterUtil.getIndentedParentBlock(currentBlock);
      if (indentedParentBlock == null) {
        return new IndentInfo(0, 0, alignOffset);
      }
      else {
        int indentOffset = indentedParentBlock.getWhiteSpace().getIndentOffset();
        if (indentOffset > alignOffset) {
          return new IndentInfo(0, 0, alignOffset);
        }
        else {
          return new IndentInfo(0, indentOffset, alignOffset - indentOffset);
        }
      }
    }
  }

  private static int getAlignOffsetBefore(@Nullable final Alignment alignment, @Nullable final LeafBlockWrapper blockAfter) {
    if (alignment == null) return -1;
    final LeafBlockWrapper alignRespBlock = ((AlignmentImpl)alignment).getOffsetRespBlockBefore(blockAfter);
    if (alignRespBlock != null) {
      return CoreFormatterUtil.getStartColumn(alignRespBlock);
    }
    else {
      return -1;
    }
  }
}
