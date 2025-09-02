// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.formatter.java;

import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.formatter.common.InjectedLanguageBlockBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class CommentWithInjectionBlock extends AbstractJavaBlock {
  private final InjectedLanguageBlockBuilder myInjectedBlockBuilder;

  public CommentWithInjectionBlock(ASTNode node,
                                   Wrap wrap,
                                   Alignment alignment,
                                   Indent indent,
                                   CommonCodeStyleSettings settings,
                                   JavaCodeStyleSettings javaSettings,
                                   @NotNull FormattingMode formattingMode) {
    super(node, wrap, alignment, indent, settings, javaSettings, formattingMode);
    myInjectedBlockBuilder = new JavaCommentInjectedBlockBuilder();
  }

  @Override
  protected List<Block> buildChildren() {
    final List<Block> result = new ArrayList<>();
    myInjectedBlockBuilder.addInjectedBlocks(result, myNode, myWrap, myAlignment, Indent.getNoneIndent());
    return result;
  }

  @Override
  public boolean isLeaf() {
    return false;
  }

  @Override
  public @NotNull ChildAttributes getChildAttributes(int newChildIndex) {
    return new ChildAttributes(Indent.getNormalIndent(), null);
  }

  @Override
  public Spacing getSpacing(Block child1, @NotNull Block child2) {
    return null;
  }

  private class JavaCommentInjectedBlockBuilder extends InjectedLanguageBlockBuilder {
    @Override
    public CodeStyleSettings getSettings() {
      return mySettings.getRootSettings();
    }

    @Override                                      
    public boolean canProcessFragment(String text, ASTNode injectionHost) {
      return true;
    }

    @Override
    public Block createBlockBeforeInjection(ASTNode node, Wrap wrap, Alignment alignment, Indent indent, final TextRange range) {
      return new PartialCommentBlock(node, wrap, alignment, indent, range);
    }

    @Override
    public Block createBlockAfterInjection(ASTNode node, Wrap wrap, Alignment alignment, Indent indent, TextRange range) {
      return new PartialCommentBlock(node, wrap, alignment, Indent.getNoneIndent(), range);
    }
  }

  private static class PartialCommentBlock extends LeafBlock {
    private final TextRange myRange;

    PartialCommentBlock(ASTNode node, Wrap wrap, Alignment alignment, Indent indent, TextRange range) {
      super(node, wrap, alignment, indent);
      myRange = range;
    }

    @Override
    public @NotNull TextRange getTextRange() {
      return myRange;
    }
  }
}
