// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.formatter.java;

import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.formatter.FormatterUtil;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public final class ChildrenBlocksBuilder {
  private final Config myConfig;

  private ChildrenBlocksBuilder(Config builder) {
    myConfig = builder;
  }

  public List<Block> buildNodeChildBlocks(ASTNode node, BlockFactory factory) {
    List<Block> blocks = new ArrayList<>();

    for (ASTNode child : node.getChildren(null)) {
      if (FormatterUtil.isWhitespaceOrEmpty(child) || child.getTextLength() == 0) {
          continue;
      }

      Alignment alignment = myConfig.getAlignment(child);

      IElementType type = child.getElementType();
      Indent indent = myConfig.getIndent(type);
      Wrap wrap = myConfig.getWrap(type);

      blocks.add(factory.createBlock(child, indent, alignment, wrap, factory.getFormattingMode()));
    }

    return blocks;
  }

  public static class Config {
    private static final Alignment NO_ALIGNMENT = Alignment.createAlignment();
    private static final Wrap NO_WRAP = Wrap.createWrap(0, false);

    private final Map<IElementType, Alignment> myAlignments = new HashMap<>();
    private final Map<IElementType, Indent> myIndents = new HashMap<>();
    private final Map<IElementType, Wrap> myWraps = new HashMap<>();

    private final Map<IElementType, Predicate<? super ASTNode>> myNoneAlignmentCondition = new HashMap<>();

    private Alignment myDefaultAlignment;
    private Indent myDefaultIndent;
    private Wrap myDefaultWrap;
    private FormattingMode myFormattingMode;

    public ChildrenBlocksBuilder createBuilder() {
      return new ChildrenBlocksBuilder(this);
    }

    public Config setDefaultAlignment(Alignment alignment) {
      myDefaultAlignment = alignment;
      return this;
    }

    public Config setDefaultWrap(Wrap wrap) {
      myDefaultWrap = wrap;
      return this;
    }

    public Config setDefaultIndent(Indent indent) {
      myDefaultIndent = indent;
      return this;
    }

    public Config setAlignment(@NotNull IElementType elementType, @NotNull Alignment alignment) {
      myAlignments.put(elementType, alignment);
      return this;
    }

    public Config setNoAlignment(IElementType elementType) {
      myAlignments.put(elementType, NO_ALIGNMENT);
      return this;
    }

    public Config setNoAlignmentIf(IElementType elementType, Predicate<? super ASTNode> applyAlignCondition) {
      myNoneAlignmentCondition.put(elementType, applyAlignCondition);
      return this;
    }

    public Config setIndent(IElementType elementType, Indent indent) {
      myIndents.put(elementType, indent);
      return this;
    }

    private Indent getIndent(IElementType elementType) {
      Indent indent = myIndents.get(elementType);
      return indent != null ? indent : myDefaultIndent;
    }

    private Alignment getAlignment(ASTNode node) {
      IElementType elementType = node.getElementType();

      Predicate<? super ASTNode> noneAlignmentCondition = myNoneAlignmentCondition.get(elementType);
      if (noneAlignmentCondition != null && noneAlignmentCondition.test(node)) {
        return null;
      }

      Alignment alignment = myAlignments.get(elementType);
      if (alignment == null) {
        return myDefaultAlignment;
      }
      return alignment == NO_ALIGNMENT ? null : alignment;
    }

    private Wrap getWrap(IElementType elementType) {
      Wrap wrap = myWraps.get(elementType);
      if (wrap == NO_WRAP) return null;
      return wrap != null ? wrap : myDefaultWrap;
    }

    public Config setNoWrap(IElementType elementType) {
      myWraps.put(elementType, NO_WRAP);
      return this;
    }

    public FormattingMode getFormattingMode() {
      return myFormattingMode;
    }

    public Config setFormattingMode(FormattingMode formattingMode) {
      myFormattingMode = formattingMode;
      return this;
    }
  }
}
