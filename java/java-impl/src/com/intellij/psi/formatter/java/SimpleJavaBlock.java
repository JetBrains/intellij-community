/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.formatting.alignment.AlignmentStrategy;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.FormatterUtil;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.impl.source.tree.StdTokenSets;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class SimpleJavaBlock extends AbstractJavaBlock {

  private int myStartOffset = -1;
  private final Map<IElementType, Wrap> myReservedWrap = new HashMap<IElementType, Wrap>();

  public SimpleJavaBlock(final ASTNode node, final Wrap wrap, final AlignmentStrategy alignment, final Indent indent, CodeStyleSettings settings) {
    super(node, wrap, alignment, indent,settings);
  }

  protected List<Block> buildChildren() {
    ASTNode child = myNode.getFirstChildNode();
    int offset = myStartOffset != -1 ? myStartOffset : child != null ? child.getTextRange().getStartOffset():0;
    final ArrayList<Block> result = new ArrayList<Block>();

    Indent indent = null;
    while (child != null) {
      if (StdTokenSets.COMMENT_BIT_SET.contains(child.getElementType()) || child.getElementType() == JavaDocElementType.DOC_COMMENT) {
        result.add(createJavaBlock(child, mySettings, Indent.getNoneIndent(), null, AlignmentStrategy.getNullStrategy()));
        indent = Indent.getNoneIndent();
      }
      else if (!FormatterUtil.containsWhiteSpacesOnly(child)) {
        break;
      }

      offset += child.getTextLength();
      child = child.getTreeNext();
    }

    myReservedAlignment = createChildAlignment();
    myReservedAlignment2 = createChildAlignment2(myReservedAlignment);
    Wrap childWrap = createChildWrap();
    while (child != null) {
      if (!FormatterUtil.containsWhiteSpacesOnly(child) && child.getTextLength() > 0){
        final ASTNode astNode = child;
        AlignmentStrategy alignmentStrategyToUse = ALIGN_IN_COLUMNS_ELEMENT_TYPES.contains(myNode.getElementType()) ? myAlignmentStrategy
          : AlignmentStrategy.wrap(chooseAlignment(myReservedAlignment, myReservedAlignment2, child));
        child = processChild(result, astNode, alignmentStrategyToUse, childWrap, indent, offset);
        if (astNode != child && child != null) {
          offset = child.getTextRange().getStartOffset();
        }
        if (indent != null && !(myNode.getPsi() instanceof PsiFile) && child != null && child.getElementType() != ElementType.MODIFIER_LIST) {
          indent = Indent.getContinuationIndent();
        }
        //indent = FormatterEx.getInstance().getContinuationIndent();
      }
      if (child != null) {
        offset += child.getTextLength();
        child = child.getTreeNext();
      }
    }

    return result;
  }

  @NotNull
  public TextRange getTextRange() {
    if (myStartOffset != -1) {
      return new TextRange(myStartOffset, myStartOffset + myNode.getTextLength());
    }
    return super.getTextRange();
  }

  @Override
  @NotNull
  public ChildAttributes getChildAttributes(final int newChildIndex) {
    if (myNode.getElementType() == ElementType.CONDITIONAL_EXPRESSION && mySettings.ALIGN_MULTILINE_TERNARY_OPERATION) {
      final Alignment usedAlignment = getUsedAlignment(newChildIndex);
      if (usedAlignment != null) {
        return new ChildAttributes(null, usedAlignment);        
      } else {
        return super.getChildAttributes(newChildIndex);
      }
    } else {
      return super.getChildAttributes(newChildIndex);
    }
  }

  @Override
  public Wrap getReservedWrap(final IElementType elementType) {
    return myReservedWrap.get(elementType);
  }

  @Override
  protected void setReservedWrap(final Wrap reservedWrap, final IElementType operationType) {
    myReservedWrap.put(operationType, reservedWrap);
  }

  public void setStartOffset(final int startOffset) {
    myStartOffset = startOffset;
    //if (startOffset != -1 && startOffset != myNode.getTextRange().getStartOffset()) {
    //  assert false;
    //}
  }
}
