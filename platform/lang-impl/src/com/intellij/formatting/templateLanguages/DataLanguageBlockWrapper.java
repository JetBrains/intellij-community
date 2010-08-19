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
package com.intellij.formatting.templateLanguages;

import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.formatter.common.AbstractBlock;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexey Chmutov
 *         Date: Jun 30, 2009
 *         Time: 7:18:37 PM
 */
public class DataLanguageBlockWrapper implements ASTBlock, BlockWithParent {
  private final Block myOriginal;
  private final Indent myIndent;
  private List<Block> myBlocks;
  private List<TemplateLanguageBlock> myTlBlocks;
  private BlockWithParent myParent;
  private DataLanguageBlockWrapper myRightHandWrapper;
  private Spacing mySpacing;

  private DataLanguageBlockWrapper(@NotNull final Block original, @Nullable final Indent indent) {
    assert !(original instanceof DataLanguageBlockWrapper) && !(original instanceof TemplateLanguageBlock);
    myOriginal = original;
    myIndent = indent;
  }

  @NotNull
  public TextRange getTextRange() {
    return myOriginal.getTextRange();
  }

  @NotNull
  public List<Block> getSubBlocks() {
    if (myBlocks == null) {
      myBlocks = buildBlocks();
    }
    return myBlocks;
  }

  private List<Block> buildBlocks() {
    assert myBlocks == null;
    if (isLeaf()) {
      return AbstractBlock.EMPTY;
    }
    final List<DataLanguageBlockWrapper> subWrappers = BlockUtil.buildChildWrappers(myOriginal);
    final List<Block> children;
    if (myTlBlocks == null) {
      children = new ArrayList<Block>(subWrappers);
    }
    else if (subWrappers.size() == 0) {
      //noinspection unchecked
      children = (List<Block>)(subWrappers.size() > 0 ? myTlBlocks : BlockUtil.splitBlockIntoFragments(myOriginal, myTlBlocks));
    }
    else {
      children = BlockUtil.mergeBlocks(myTlBlocks, subWrappers);
    }
    //BlockUtil.printBlocks(getTextRange(), children);
    return BlockUtil.setParent(children, this);
  }

  public Wrap getWrap() {
    return myOriginal.getWrap();
  }

  @NotNull
  public ChildAttributes getChildAttributes(final int newChildIndex) {
    return myOriginal.getChildAttributes(newChildIndex);
  }

  public Indent getIndent() {
    return myOriginal.getIndent();
  }

  public Alignment getAlignment() {
    return myOriginal.getAlignment();
  }

  @Nullable
  public Spacing getSpacing(Block child1, Block child2) {
    if (child1 instanceof DataLanguageBlockWrapper && child2 instanceof DataLanguageBlockWrapper) {
      return myOriginal.getSpacing(((DataLanguageBlockWrapper)child1).myOriginal, ((DataLanguageBlockWrapper)child2).myOriginal);
    }
    return null;
  }

  public boolean isIncomplete() {
    return myOriginal.isIncomplete();
  }

  public boolean isLeaf() {
    return myTlBlocks == null && myOriginal.isLeaf();
  }

  void addTlChild(TemplateLanguageBlock tlBlock) {
    assert myBlocks == null;
    if (myTlBlocks == null) {
      myTlBlocks = new ArrayList<TemplateLanguageBlock>(5);
    }
    myTlBlocks.add(tlBlock);
    tlBlock.setParent(this);
  }

  public Block getOriginal() {
    return myOriginal;
  }

  @Override
  public String toString() {
    String tlBlocksInfo = " TlBlocks " + (myTlBlocks == null ? "0" : myTlBlocks.size()) + "|" + getTextRange() + "|";
    return tlBlocksInfo + myOriginal.toString();
  }

  @Nullable
  public static DataLanguageBlockWrapper create(@NotNull final Block original, @Nullable final Indent indent) {
    final boolean doesntNeedWrapper = original instanceof ASTBlock && ((ASTBlock)original).getNode() instanceof OuterLanguageElement;
    return doesntNeedWrapper ? null : new DataLanguageBlockWrapper(original, indent);
  }

  @Nullable
  public ASTNode getNode() {
    return myOriginal instanceof ASTBlock ? ((ASTBlock)myOriginal).getNode() : null;
  }

  public BlockWithParent getParent() {
    return myParent;
  }

  public void setParent(BlockWithParent parent) {
    myParent = parent;
  }

  public void setRightHandSpacing(DataLanguageBlockWrapper rightHandWrapper, Spacing spacing) {
    myRightHandWrapper = rightHandWrapper;
    mySpacing = spacing;
  }

  @Nullable
  public Spacing getRightHandSpacing(DataLanguageBlockWrapper rightHandWrapper) {
    return myRightHandWrapper == rightHandWrapper ? mySpacing : null;
  }
}
