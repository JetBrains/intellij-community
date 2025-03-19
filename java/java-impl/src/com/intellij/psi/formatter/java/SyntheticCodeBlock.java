// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.formatter.java;

import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.formatter.common.AbstractBlock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class SyntheticCodeBlock implements Block, JavaBlock{
  private final List<Block> mySubBlocks;
  private final Alignment myAlignment;
  private final Indent myIndentContent;
  private final CommonCodeStyleSettings mySettings;
  private final JavaCodeStyleSettings myJavaSettings;
  private final Wrap myWrap;

  private static final Logger LOG = Logger.getInstance(SyntheticCodeBlock.class);

  private final TextRange myTextRange;

  private ChildAttributes myChildAttributes;
  private boolean myIsIncomplete;

  public SyntheticCodeBlock(List<Block> subBlocks,
                            Alignment alignment,
                            CommonCodeStyleSettings settings,
                            JavaCodeStyleSettings javaSettings,
                            Indent indent,
                            Wrap wrap)
  {
    myJavaSettings = javaSettings;
    myIndentContent = indent;
    if (subBlocks.isEmpty()) {
      LOG.assertTrue(false);
    }
    mySubBlocks = new ArrayList<>(subBlocks);
    myAlignment = alignment;
    mySettings = settings;
    myWrap = wrap;
    myTextRange = new TextRange(mySubBlocks.get(0).getTextRange().getStartOffset(),
                                mySubBlocks.get(mySubBlocks.size() - 1).getTextRange().getEndOffset());
  }

  @Override
  public @NotNull TextRange getTextRange() {
    return myTextRange;
  }

  @Override
  public @NotNull List<Block> getSubBlocks() {
    return mySubBlocks;
  }

  @Override
  public Wrap getWrap() {
    return myWrap;
  }

  @Override
  public Indent getIndent() {
    return myIndentContent;
  }

  @Override
  public Alignment getAlignment() {
    return myAlignment;
  }

  @Override
  public Spacing getSpacing(Block child1, @NotNull Block child2) {
    return JavaSpacePropertyProcessor.getSpacing(child2, mySettings, myJavaSettings);
  }

  @Override
  public String toString() {
    ASTNode treeNode = null;
    Block child = mySubBlocks.get(0);
    while (treeNode == null) {
      if (child instanceof AbstractBlock) {
        treeNode = ((AbstractBlock)child).getNode();
      }
      else if (child instanceof SyntheticCodeBlock) {
        child = ((SyntheticCodeBlock)child).mySubBlocks.get(0);
      }
      else {
        break;
      }
    }
    final TextRange textRange = getTextRange();
    if (treeNode != null) {
      PsiElement psi = treeNode.getPsi();
      if (psi != null) {
        PsiFile file = psi.getContainingFile();
        if (file != null) {
          return file.getText().subSequence(textRange.getStartOffset(), textRange.getEndOffset()) + " " + textRange;
        }
      }
    }
    return getClass().getName() + ": " + textRange;
  }

  @Override
  public @Nullable ASTNode getFirstTreeNode() {
    return AbstractJavaBlock.getTreeNode(mySubBlocks.get(0));
  }

  public void setChildAttributes(final ChildAttributes childAttributes) {
    myChildAttributes = childAttributes;
  }

  @Override
  public @NotNull ChildAttributes getChildAttributes(final int newChildIndex) {
    if (myChildAttributes != null) {
      return myChildAttributes;
    } else {
      Alignment alignment = null;
      if (mySubBlocks.size() > newChildIndex) {
        Block block = mySubBlocks.get(newChildIndex);
        alignment = block.getAlignment();
      }
      else if (mySubBlocks.size() == newChildIndex) {
        if (isRParenth(getRightMostBlock())) {
          alignment = getDotAlignment();
        }
      }
      
      return new ChildAttributes(getIndent(), alignment);
    }
  }

  private static boolean isRParenth(Block sibling) {
    if (sibling instanceof LeafBlock) {
      ASTNode node = ((LeafBlock)sibling).getNode();
      return node != null && node.getElementType() == JavaTokenType.RPARENTH;
    }
    return false;
  }

  private @Nullable Alignment getDotAlignment() {
    if (mySubBlocks.size() > 1) {
      Block block = mySubBlocks.get(1);
      if (isDotFirst(block)) {
        return block.getAlignment();
      }
    }
    return null;
  }

  private static boolean isDotFirst(final Block block) {
    Block current = block;
    while (!current.getSubBlocks().isEmpty()) {
      current = current.getSubBlocks().get(0);
    }
    ASTNode node = current instanceof LeafBlock ? ((LeafBlock)current).getNode() : null;
    return node != null && node.getElementType() == JavaTokenType.DOT;
  }

  private @Nullable Block getRightMostBlock() {
    Block rightMost = null;
    List<Block> subBlocks = getSubBlocks();
    while (!subBlocks.isEmpty()) {
      int lastIndex = subBlocks.size() - 1;
      rightMost = subBlocks.get(lastIndex);
      subBlocks = rightMost.getSubBlocks();
    }
    return rightMost;
  }

  @Override
  public boolean isIncomplete() {
    if (myIsIncomplete) return true;
    return getSubBlocks().get(getSubBlocks().size() - 1).isIncomplete();
  }

  @Override
  public boolean isLeaf() {
    return false;
  }

  public void setIsIncomplete(final boolean isIncomplete) {
    myIsIncomplete = isIncomplete;
  }
}
