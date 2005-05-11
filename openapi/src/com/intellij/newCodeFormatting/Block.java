package com.intellij.newCodeFormatting;

import com.intellij.openapi.util.TextRange;

import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface Block {
  @NotNull TextRange getTextRange();

  @NotNull List<Block> getSubBlocks();

  @Nullable Wrap getWrap();

  @Nullable Indent getIndent();

  @Nullable Alignment getAlignment();

  @Nullable SpaceProperty getSpaceProperty(Block child1, Block child2);

  @NotNull ChildAttributes getChildAttributes(final int newChildIndex);

  boolean isIncopleted();
}
