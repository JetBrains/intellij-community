package com.intellij.psi.formatter.java;

import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.common.AbstractBlock;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class SyntheticCodeBlock implements Block, JavaBlock{
  private final List<Block> mySubBlocks;
  private final Alignment myAlignment;
  private final Indent myIndentContent;
  private final CodeStyleSettings mySettings;
  private final Wrap myWrap;

  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.formatter.newXmlFormatter.java.SyntheticCodeBlock");

  private final TextRange myTextRange;

  private ChildAttributes myChildAttributes;
  private boolean myIsIncomplete = false;

  public SyntheticCodeBlock(final List<Block> subBlocks,
                            final Alignment alignment,
                            CodeStyleSettings settings,
                            Indent indent,
                            Wrap wrap) {
    myIndentContent = indent;
    if (subBlocks.isEmpty()) {
      LOG.assertTrue(false);
    }
    mySubBlocks = new ArrayList<Block>(subBlocks);
    myAlignment = alignment;
    mySettings = settings;
    myWrap = wrap;
    myTextRange = new TextRange(mySubBlocks.get(0).getTextRange().getStartOffset(),
                                mySubBlocks.get(mySubBlocks.size() - 1).getTextRange().getEndOffset());
  }

  @NotNull
  public TextRange getTextRange() {
    return myTextRange;
  }

  @NotNull
  public List<Block> getSubBlocks() {
    return mySubBlocks;
  }

  public Wrap getWrap() {
    return myWrap;
  }

  public Indent getIndent() {
    return myIndentContent;
  }

  public Alignment getAlignment() {
    return myAlignment;
  }

  public Spacing getSpacing(Block child1, Block child2) {
    return JavaSpacePropertyProcessor.getSpacing(AbstractJavaBlock.getTreeNode(child2), mySettings);
  }

  public String toString() {
    final ASTNode treeNode = ((AbstractBlock)mySubBlocks.get(0)).getNode();
    final TextRange textRange = getTextRange();
    return treeNode.getPsi().getContainingFile().getText().subSequence(textRange.getStartOffset(), textRange.getEndOffset()).toString();
  }

  public ASTNode getFirstTreeNode() {
    return AbstractJavaBlock.getTreeNode(mySubBlocks.get(0));
  }

  public void setChildAttributes(final ChildAttributes childAttributes) {
    myChildAttributes = childAttributes;
  }

  @NotNull
  public ChildAttributes getChildAttributes(final int newChildIndex) {
    if (myChildAttributes != null) {
      return myChildAttributes;
    } else {
      return new ChildAttributes(getIndent(), null);
    }
  }

  public boolean isIncomplete() {
    if (myIsIncomplete) return true;
    return getSubBlocks().get(getSubBlocks().size() - 1).isIncomplete();
  }

  public boolean isLeaf() {
    return false;
  }

  public void setIsIncomplete(final boolean isIncomplete) {
    myIsIncomplete = isIncomplete;
  }
}
