// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.formatting;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class CompositeBlockWrapper extends AbstractBlockWrapper{
  private List<AbstractBlockWrapper> myChildren;
  private ProbablyIncreasingLowerboundAlgorithm<AbstractBlockWrapper> myPrevBlockCalculator = null;

  public CompositeBlockWrapper(final Block block, final @NotNull WhiteSpace whiteSpaceBefore, final @Nullable CompositeBlockWrapper parent) {
    super(block, whiteSpaceBefore, parent, block.getTextRange());
  }
  
  public List<AbstractBlockWrapper> getChildren() {
    return myChildren;
  }

  public void setChildren(final List<AbstractBlockWrapper> children) {
    myChildren = children;
    if (myPrevBlockCalculator != null) {
      myPrevBlockCalculator.setBlocksList(myChildren);
    }
  }

  @Override
  public void reset() {
    super.reset();

    if (myChildren != null) {
      for(AbstractBlockWrapper wrapper : myChildren) {
        assert wrapper != this;
        wrapper.reset();
      }
    }
  }

  @Override
  protected boolean indentAlreadyUsedBefore(final AbstractBlockWrapper child) {
    for (AbstractBlockWrapper childBefore : myChildren) {
      if (childBefore == child) return false;
      if (childBefore.getWhiteSpace().containsLineFeeds()) return true;
    }
    return false;
  }

  @Override
  public IndentData getNumberOfSymbolsBeforeBlock() {
    if (myChildren == null || myChildren.isEmpty()) {
      return new IndentData(0, 0);
    }
    return myChildren.get(0).getNumberOfSymbolsBeforeBlock();
  }

  @Override
  protected LeafBlockWrapper getPreviousBlock() {
    if (myChildren == null || myChildren.isEmpty()) {
      return null;
    }
    return myChildren.get(0).getPreviousBlock();
  }

  @Override
  public void dispose() {
    super.dispose();
    myChildren = null;
    myPrevBlockCalculator = null;
  }

  /**
   * Tries to find child block of the current composite block that contains line feeds and starts before the given block
   * (i.e. its {@link AbstractBlockWrapper#getStartOffset() start offset} is less than start offset of the given block).
   *
   * @param current   block that defines right boundary for child blocks processing
   * @return          last child block that contains line feeds and starts before the given block if any;
   *                  {@code null} otherwise
   */
  public @Nullable AbstractBlockWrapper getPrevIndentedSibling(final @NotNull AbstractBlockWrapper current) {
    if (myChildren.size() > 10) {
      return getPrevIndentedSiblingFast(current);
    }

    AbstractBlockWrapper candidate = null;
    for (AbstractBlockWrapper child : myChildren) {
      if (child.getStartOffset() >= current.getStartOffset()) return candidate;
      if (child.getWhiteSpace().containsLineFeeds()) candidate = child;
    }

    return candidate;
  }

  private @Nullable AbstractBlockWrapper getPrevIndentedSiblingFast(final @NotNull AbstractBlockWrapper current) {
    if (myPrevBlockCalculator == null) {
      myPrevBlockCalculator = new ProbablyIncreasingLowerboundAlgorithm<>(myChildren);
    }

    final List<AbstractBlockWrapper> leftBlocks = myPrevBlockCalculator.getLeftSubList(current);
    for (int i = leftBlocks.size() - 1; i >= 0; i--) {
      final AbstractBlockWrapper child = leftBlocks.get(i);
      if (child.getWhiteSpace().containsLineFeeds()) {
        return child;
      }
    }
    return null;
  }
}
