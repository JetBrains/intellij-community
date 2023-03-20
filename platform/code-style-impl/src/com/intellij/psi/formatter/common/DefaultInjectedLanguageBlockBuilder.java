// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.formatter.common;

import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class DefaultInjectedLanguageBlockBuilder extends InjectedLanguageBlockBuilder {

  @NotNull private final CodeStyleSettings mySettings;

  public DefaultInjectedLanguageBlockBuilder(@NotNull CodeStyleSettings settings) {
    mySettings = settings;
  }

  @NotNull
  @Override
  public CodeStyleSettings getSettings() {
    return mySettings;
  }

  @Override
  public boolean canProcessFragment(String text, ASTNode injectionHost) {
    return true;
  }

  @Override
  public Block createBlockBeforeInjection(ASTNode node, Wrap wrap, Alignment alignment, Indent indent, final TextRange range) {
    return new GlueBlock(node, wrap, alignment, indent, range);
  }

  @Override
  public Block createBlockAfterInjection(ASTNode node, Wrap wrap, Alignment alignment, Indent indent, TextRange range) {
    return new GlueBlock(node, wrap, alignment, Indent.getNoneIndent(), range);
  }

  private static final class GlueBlock extends AbstractBlock {

    @NotNull private final Indent    myIndent;
    @NotNull private final TextRange myRange;

    private GlueBlock(@NotNull ASTNode node,
                      @Nullable Wrap wrap,
                      @Nullable Alignment alignment,
                      @NotNull Indent indent,
                      @NotNull TextRange range)
    {
      super(node, wrap, alignment);
      myIndent = indent;
      myRange = range;
    }

    @NotNull
    @Override
    public TextRange getTextRange() {
      return myRange;
    }

    @Override
    protected List<Block> buildChildren() {
      return AbstractBlock.EMPTY;
    }

    @NotNull
    @Override
    public Indent getIndent() {
      return myIndent;
    }

    @Nullable
    @Override
    public Spacing getSpacing(@Nullable Block child1, @NotNull Block child2) {
      return null;
    }

    @Override
    public boolean isLeaf() {
      return true;
    }
  }
}
