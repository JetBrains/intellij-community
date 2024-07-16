// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.formatter.java;

import com.intellij.formatting.*;
import com.intellij.formatting.alignment.AlignmentStrategy;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.formatter.FormatterUtil;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.StdTokenSets;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SimpleJavaBlock extends AbstractJavaBlock {
  private final Map<IElementType, Wrap> myReservedWrap = new HashMap<>();
  private int myStartOffset = -1;
  private int myCurrentOffset;
  private Indent myCurrentIndent;
  private ASTNode myCurrentChild;

  public SimpleJavaBlock(ASTNode node,
                         Wrap wrap,
                         AlignmentStrategy alignment,
                         Indent indent,
                         CommonCodeStyleSettings settings,
                         JavaCodeStyleSettings javaSettings,
                         @NotNull FormattingMode formattingMode) {
    super(node, wrap, alignment, indent, settings, javaSettings, formattingMode);
  }

  @Override
  protected List<Block> buildChildren() {
    myCurrentChild = myNode.getFirstChildNode();
    myCurrentOffset = myStartOffset;
    if (myCurrentOffset == -1) {
      myCurrentOffset = myCurrentChild != null ? myCurrentChild.getTextRange().getStartOffset() : 0;
    }

    final List<Block> result = new ArrayList<>();

    myCurrentIndent = null;
    processHeadCommentsAndWhiteSpaces(result);

    calculateReservedAlignments();

    Wrap childWrap = createChildWrap();
    processRemainingChildren(result, childWrap);

    return result;
  }

  private void calculateReservedAlignments() {
    myReservedAlignment = createChildAlignment();

    IElementType nodeType = myNode.getElementType();
    if (nodeType == JavaElementType.CONDITIONAL_EXPRESSION && mySettings.ALIGN_MULTILINE_TERNARY_OPERATION) {
      myReservedAlignment2 = myReservedAlignment != null ? Alignment.createChildAlignment(myReservedAlignment)
                                                         : Alignment.createAlignment();
    }
  }

  private void processRemainingChildren(List<Block> result, Wrap childWrap) {
    while (myCurrentChild != null) {
      if (isNotEmptyNode(myCurrentChild)) {
        final ASTNode astNode = myCurrentChild;
        AlignmentStrategy alignmentStrategyToUse = AlignmentStrategy.wrap(chooseAlignment(myReservedAlignment, myReservedAlignment2, myCurrentChild));

        if (myNode.getElementType() == JavaElementType.FIELD
            || myNode.getElementType() == JavaElementType.DECLARATION_STATEMENT
            || myNode.getElementType() == JavaElementType.LOCAL_VARIABLE) {
          alignmentStrategyToUse = myAlignmentStrategy;
        }

        myCurrentChild = processChild(result, astNode, alignmentStrategyToUse, childWrap, myCurrentIndent, myCurrentOffset);
        if (astNode != myCurrentChild && myCurrentChild != null) {
          myCurrentOffset = myCurrentChild.getTextRange().getStartOffset();
        }
        if (myCurrentIndent != null &&
            !(myNode.getPsi() instanceof PsiFile) &&
            myCurrentChild != null && myCurrentChild.getElementType() != JavaElementType.MODIFIER_LIST) {
          myCurrentIndent = Indent.getContinuationIndent(myIndentSettings.USE_RELATIVE_INDENTS);
        }
      }

      if (myCurrentChild != null) {
        myCurrentOffset += myCurrentChild.getTextLength();
        myCurrentChild = myCurrentChild.getTreeNext();
      }
    }
  }

  private void processHeadCommentsAndWhiteSpaces(@NotNull List<? super Block> result) {
    while (myCurrentChild != null) {
      if (StdTokenSets.COMMENT_BIT_SET.contains(myCurrentChild.getElementType()) || myCurrentChild.getElementType() == JavaDocElementType.DOC_COMMENT) {
        Block commentBlock = createJavaBlock(
          myCurrentChild,
          mySettings, myJavaSettings,
          Indent.getNoneIndent(), null, AlignmentStrategy.getNullStrategy(),
          getFormattingMode()
        );
        result.add(commentBlock);
        myCurrentIndent = Indent.getNoneIndent();
      }
      else if (!FormatterUtil.containsWhiteSpacesOnly(myCurrentChild)) {
        break;
      }

      myCurrentOffset += myCurrentChild.getTextLength();
      myCurrentChild = myCurrentChild.getTreeNext();
    }
  }

  private static boolean isNotEmptyNode(@NotNull ASTNode child) {
    return !FormatterUtil.containsWhiteSpacesOnly(child) && child.getTextLength() > 0;
  }

  @Override
  public @NotNull TextRange getTextRange() {
    return myStartOffset == -1 ? super.getTextRange() : new TextRange(myStartOffset, myStartOffset + myNode.getTextLength());
  }

  @Override
  public @NotNull ChildAttributes getChildAttributes(final int newChildIndex) {
    if (myNode.getElementType() == JavaElementType.CONDITIONAL_EXPRESSION && mySettings.ALIGN_MULTILINE_TERNARY_OPERATION) {
      final Alignment usedAlignment = getUsedAlignment(newChildIndex);
      if (usedAlignment != null) {
        return new ChildAttributes(null, usedAlignment);
      }
      else {
        return super.getChildAttributes(newChildIndex);
      }
    }
    else if (myNode.getElementType() == JavaElementType.SWITCH_LABEL_STATEMENT) {
      return new ChildAttributes(Indent.getNormalIndent(), null);
    }
    else {
      return super.getChildAttributes(newChildIndex);
    }
  }

  @Override
  public Wrap getReservedWrap(final IElementType elementType) {
    return myReservedWrap.get(elementType);
  }

  @Override
  public void setReservedWrap(final Wrap reservedWrap, final IElementType operationType) {
    myReservedWrap.put(operationType, reservedWrap);
  }

  public void setStartOffset(final int startOffset) {
    myStartOffset = startOffset;
  }
}
