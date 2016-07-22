/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

/**
 * @author nik
 */
public class CommentWithInjectionBlock extends AbstractJavaBlock {
  private final InjectedLanguageBlockBuilder myInjectedBlockBuilder;

  public CommentWithInjectionBlock(ASTNode node,
                                   Wrap wrap,
                                   Alignment alignment,
                                   Indent indent,
                                   CommonCodeStyleSettings settings,
                                   JavaCodeStyleSettings javaSettings) {
    super(node, wrap, alignment, indent, settings, javaSettings);
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

  @NotNull
  @Override
  public ChildAttributes getChildAttributes(int newChildIndex) {
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

    public PartialCommentBlock(ASTNode node, Wrap wrap, Alignment alignment, Indent indent, TextRange range) {
      super(node, wrap, alignment, indent);
      myRange = range;
    }

    @NotNull
    @Override
    public TextRange getTextRange() {
      return myRange;
    }
  }
}
