/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.psi.formatter.java;

import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.formatter.common.ExtraRangesProvider;
import com.intellij.psi.formatter.common.NodeIndentRangesCalculator;
import com.intellij.psi.impl.source.codeStyle.ShiftIndentInsideHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class LeafBlock implements ASTBlock, ExtraRangesProvider {
  private int myStartOffset = -1;
  private final ASTNode myNode;
  private final Wrap myWrap;
  private final Alignment myAlignment;

  private static final ArrayList<Block> EMPTY_SUB_BLOCKS = new ArrayList<>();
  private final Indent myIndent;

  public LeafBlock(final ASTNode node,
                   final Wrap wrap,
                   final Alignment alignment,
                   Indent indent)
  {
    myNode = node;
    myWrap = wrap;
    myAlignment = alignment;
    myIndent = indent;
  }

  @Override
  public ASTNode getNode() {
    return myNode;
  }

  @Override
  @NotNull
  public TextRange getTextRange() {
    if (myStartOffset != -1) {
      return new TextRange(myStartOffset, myStartOffset + myNode.getTextLength());
    }
    return myNode.getTextRange();
  }

  @Override
  @NotNull
  public List<Block> getSubBlocks() {
    return EMPTY_SUB_BLOCKS;
  }

  @Override
  public Wrap getWrap() {
    return myWrap;
  }

  @Override
  public Indent getIndent() {
    return myIndent;
  }

  @Override
  public Alignment getAlignment() {
    return myAlignment;
  }

  @Override
  public Spacing getSpacing(Block child1, @NotNull Block child2) {
    return null;
  }

  public ASTNode getTreeNode() {
    return myNode;
  }

  @Override
  @NotNull
  public ChildAttributes getChildAttributes(final int newChildIndex) {
    return new ChildAttributes(getIndent(), null);
  }

  @Override
  public boolean isIncomplete() {
    return false;
  }

  @Override
  public boolean isLeaf() {
    return ShiftIndentInsideHelper.mayShiftIndentInside(myNode);
  }

  public void setStartOffset(final int startOffset) {
    myStartOffset = startOffset;
   // if (startOffset != -1) assert startOffset == myNode.getTextRange().getStartOffset();
  }

  @Override
  @Nullable
  public List<TextRange> getExtraRangesToFormat(@NotNull FormattingRangesInfo info) {
    int startOffset = getTextRange().getStartOffset();
    if (info.isOnInsertedLine(startOffset) && myNode.getTextLength() == 1 && myNode.textContains('}')) {
      ASTNode parent = myNode.getTreeParent();
      return new NodeIndentRangesCalculator(parent).calculateExtraRanges();
    }
    return null;
  }
  
}
