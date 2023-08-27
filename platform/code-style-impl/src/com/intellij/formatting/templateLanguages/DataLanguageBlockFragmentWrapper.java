// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.formatting.templateLanguages;

import com.intellij.formatting.*;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.formatter.common.AbstractBlock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class DataLanguageBlockFragmentWrapper implements Block {
  private final Block myOwner;
  private final TextRange myRange;

  public DataLanguageBlockFragmentWrapper(final @NotNull Block owner, final @NotNull TextRange range) {
    myOwner = owner;
    myRange = range;
  }

  @Override
  public @NotNull TextRange getTextRange() {
    return myRange;
  }

  @Override
  public @NotNull List<Block> getSubBlocks() {
    return AbstractBlock.EMPTY;
  }

  @Override
  public Wrap getWrap() {
    return myOwner.getWrap();
  }

  @Override
  public Indent getIndent() {
    return myOwner.getIndent();
  }

  @Override
  public Alignment getAlignment() {
    return myOwner.getAlignment();
  }

  @Override
  public @Nullable Spacing getSpacing(Block child1, @NotNull Block child2) {
    return Spacing.getReadOnlySpacing();
  }

  @Override
  public @NotNull ChildAttributes getChildAttributes(int newChildIndex) {
    return myOwner.getChildAttributes(newChildIndex);
  }

  @Override
  public boolean isIncomplete() {
    return myOwner.isIncomplete();
  }

  @Override
  public boolean isLeaf() {
    return true;
  }

  @Override
  public String toString() {
    return "Fragment " + getTextRange();
  }
}
