// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.formatter.java;

import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;


class IndentBlock implements Block {
    private final ASTNode myDotNode;
    private final int myIndentSize;

    IndentBlock(@NotNull ASTNode dotNode, int indentSize) {
      myDotNode = dotNode;
      myIndentSize = indentSize;
    }

    @NotNull
    @Override
    public TextRange getTextRange() {
      int start = Math.max(0, myDotNode.getTextRange().getStartOffset() - myIndentSize);
      int end = myDotNode.getStartOffset();
      return new TextRange(start, end);
    }

    @NotNull
    @Override
    public List<Block> getSubBlocks() {
      return Collections.emptyList();
    }

    @Nullable
    @Override
    public Wrap getWrap() {
      return null;
    }

    @Nullable
    @Override
    public Indent getIndent() {
      return Indent.getNoneIndent();
    }

    @Nullable
    @Override
    public Alignment getAlignment() {
      return null;
    }

    @Nullable
    @Override
    public Spacing getSpacing(@Nullable Block child1, @NotNull Block child2) {
      return Spacing.getReadOnlySpacing();
    }

    @NotNull
    @Override
    public ChildAttributes getChildAttributes(int newChildIndex) {
      return new ChildAttributes(null, null);
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
