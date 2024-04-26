// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting.templateLanguages;

import com.intellij.formatting.*;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * A block that's created when template & template data language blocks overlap in an irreconcilable way. The block covers the entire overlap
 * area and isn't reformatted.
 */
final class ErrorLeafBlock implements Block {
  private final int myStartOffset;
  private final int myEndOffset;

  ErrorLeafBlock(int startOffset, int endOffset) {
    myStartOffset = startOffset;
    myEndOffset = endOffset;
  }

  @Override
  public @NotNull TextRange getTextRange() {
    return TextRange.create(myStartOffset, myEndOffset);
  }

  @Override
  public @NotNull List<Block> getSubBlocks() {
    return Collections.emptyList();
  }

  @Override
  public @Nullable Wrap getWrap() {
    return null;
  }

  @Override
  public @Nullable Indent getIndent() {
    return null;
  }

  @Override
  public @Nullable Alignment getAlignment() {
    return null;
  }

  @Override
  public @Nullable Spacing getSpacing(@Nullable Block child1, @NotNull Block child2) {
    return null;
  }

  @Override
  public @NotNull ChildAttributes getChildAttributes(int newChildIndex) {
    return new ChildAttributes(null, null);
  }

  @Override
  public boolean isIncomplete() {
    return true;
  }

  @Override
  public boolean isLeaf() {
    return true;
  }
}
