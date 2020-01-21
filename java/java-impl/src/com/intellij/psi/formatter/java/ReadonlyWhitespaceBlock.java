// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi.formatter.java;

import com.intellij.formatting.*;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class ReadonlyWhitespaceBlock implements Block {
  private final TextRange myRange;
  private final Wrap myWrap;
  private final Alignment myAlignment;
  private final Indent myIndent;

  public ReadonlyWhitespaceBlock(final TextRange range, final Wrap wrap, final Alignment alignment, final Indent indent) {
    myRange = range;
    myWrap = wrap;
    myAlignment = alignment;
    myIndent = indent;
  }

  @Override
  @NotNull
  public TextRange getTextRange() {
    return myRange;
  }

  @Override
  @NotNull
  public List<Block> getSubBlocks() {
    return Collections.emptyList();
  }

  @Override
  @Nullable
  public Wrap getWrap() {
    return myWrap;
  }

  @Override
  @Nullable
  public Indent getIndent() {
    return myIndent;
  }

  @Override
  @Nullable
  public Alignment getAlignment() {
    return myAlignment;
  }

  @Override
  @Nullable
  public Spacing getSpacing(Block child1, @NotNull Block child2) {
    return null;
  }

  @Override
  @NotNull
  public ChildAttributes getChildAttributes(final int newChildIndex) {
    return ChildAttributes.DELEGATE_TO_NEXT_CHILD;
  }

  @Override
  public boolean isIncomplete() {
    return false;
  }

  @Override
  public boolean isLeaf() {
    return true;
  }
}
