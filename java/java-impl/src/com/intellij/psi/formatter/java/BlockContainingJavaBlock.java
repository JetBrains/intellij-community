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
import com.intellij.lang.ASTNode;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.FormatterUtil;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.impl.source.tree.StdTokenSets;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class BlockContainingJavaBlock extends AbstractJavaBlock{

  private final static int BEFORE_FIRST = 0;
  private final static int BEFORE_BLOCK = 1;
  private final static int AFTER_ELSE = 2;

  private final List<Indent> myIndentsBefore = new ArrayList<Indent>();

  public BlockContainingJavaBlock(final ASTNode node, final Wrap wrap, final Alignment alignment, final Indent indent, CodeStyleSettings settings) {
    super(node, wrap, alignment, indent, settings);
  }
  protected List<Block> buildChildren() {
    final ArrayList<Block> result = new ArrayList<Block>();
    Alignment childAlignment = createChildAlignment();
    Wrap childWrap = createChildWrap();

    buildChildren(result, childAlignment, childWrap);

    return result;

  }

  private void buildChildren(final ArrayList<Block> result, final Alignment childAlignment, final Wrap childWrap) {
    ASTNode child = myNode.getFirstChildNode();
    ASTNode prevChild = null;

    int state = BEFORE_FIRST;

    while (child != null) {
      if (!FormatterUtil.containsWhiteSpacesOnly(child) && child.getTextLength() > 0){
        final Indent indent = calcIndent(child,  state);
        myIndentsBefore.add(calcIndentBefore(child,  state));
        state = calcNewState(child, state);

        // The general idea is that it's possible that there are comment lines before method declaration line and that they have
        // different indents. Example:
        //
        //     // This is comment before method
        //               void foo() {}
        //
        // We want to have the comment and method as distinct blocks then in order to correctly process indentation for inner method
        // elements. See IDEA-53778 for example of situation when it is significant.
        if (prevChild != null && myNode.getElementType() == ElementType.METHOD
            && JavaTokenType.COMMENT_BIT_SET.contains(prevChild.getElementType())
            && !JavaTokenType.COMMENT_BIT_SET.contains(child.getElementType()))
        {
          prevChild = child;
          child = composeCodeBlock(result, child, Indent.getNoneIndent(), 0, null);
        }
        else {
          prevChild = child;
          child = processChild(result, child, childAlignment, childWrap, indent);
        }                
        for (int i = myIndentsBefore.size(); i < result.size(); i++) {
          myIndentsBefore.add(Indent.getContinuationIndent());
        }
      }
      if (child != null) {
        child = child.getTreeNext();
      }
    }
  }

  private static int calcNewState(final ASTNode child, final int state) {
    if (state == BEFORE_FIRST) {
      if (child.getElementType() == ElementType.ELSE_KEYWORD) {
        return AFTER_ELSE;
      }
      if (StdTokenSets.COMMENT_BIT_SET.contains(child.getElementType())) {
        return BEFORE_FIRST;
      }
      if (child.getElementType() == ElementType.CATCH_SECTION) {
        return BEFORE_FIRST;
      }
    } else if (state == BEFORE_BLOCK){
      if (child.getElementType() == ElementType.ELSE_KEYWORD) {
        return AFTER_ELSE;
      }
      if (child.getElementType() == ElementType.BLOCK_STATEMENT) {
        return BEFORE_FIRST;
      }
      if (child.getElementType() == ElementType.CODE_BLOCK) {
        return BEFORE_FIRST;
      }

    }
    return BEFORE_BLOCK;
  }

  private Indent calcIndent(final ASTNode child, final int state) {
    if (state == AFTER_ELSE && child.getElementType() == ElementType.IF_STATEMENT) {
      if (mySettings.SPECIAL_ELSE_IF_TREATMENT) {
        return Indent.getNoneIndent();
      } else {
        return getCodeBlockInternalIndent(1);
      }
    }
    if (isSimpleStatement(child)){
      return createNormalIndent(1);
    }
    if (child.getElementType() == ElementType.ELSE_KEYWORD)
      return Indent.getNoneIndent();
    if (state == BEFORE_FIRST) {
      return Indent.getNoneIndent();
    }
    else if (child.getElementType() == ElementType.WHILE_KEYWORD) {
      return Indent.getNoneIndent();
    }
    else {
      if (isPartOfCodeBlock(child)) {
        return getCodeBlockExternalIndent();
      }
      else if (isSimpleStatement(child)){
        return getCodeBlockInternalIndent(1);
      }
      else if (StdTokenSets.COMMENT_BIT_SET.contains(child.getElementType())) {
        return getCodeBlockInternalIndent(1);
      }
      else {
        return Indent.getContinuationIndent();
      }
    }
  }

  private Indent calcIndentBefore(final ASTNode child, final int state) {
    if (state == AFTER_ELSE) {
      if (!mySettings.SPECIAL_ELSE_IF_TREATMENT) {
        return getCodeBlockInternalIndent(1);
      } else {
        return getCodeBlockExternalIndent();
      }
    }
    if (state == BEFORE_BLOCK && (isSimpleStatement(child) || child.getElementType() == ElementType.BLOCK_STATEMENT)){
      return getCodeBlockInternalIndent(0);
    }
    if (state == BEFORE_FIRST) {
      return getCodeBlockExternalIndent();
    }
    if (child.getElementType() == ElementType.ELSE_KEYWORD)
      return getCodeBlockExternalIndent();

    return Indent.getContinuationIndent();
  }

  private static boolean isSimpleStatement(final ASTNode child) {
    if (child.getElementType() == ElementType.BLOCK_STATEMENT) return false;
    if (!ElementType.STATEMENT_BIT_SET.contains(child.getElementType())) return false;
    return isStatement(child, child.getTreeParent());
  }

  private static boolean isPartOfCodeBlock(final ASTNode child) {
    if (child == null) return false;
    if (child.getElementType() == ElementType.BLOCK_STATEMENT) return true;
    if (child.getElementType() == ElementType.CODE_BLOCK) return true;

    if (FormatterUtil.containsWhiteSpacesOnly(child)) return isPartOfCodeBlock(child.getTreeNext());
    if (child.getElementType() == ElementType.END_OF_LINE_COMMENT) return isPartOfCodeBlock(child.getTreeNext());
    return child.getElementType() == JavaDocElementType.DOC_COMMENT;
  }



  @NotNull
  public ChildAttributes getChildAttributes(final int newChildIndex) {
    if (isAfter(newChildIndex, new IElementType[]{JavaDocElementType.DOC_COMMENT})) {
      return new ChildAttributes(Indent.getNoneIndent(), null);
    }

    if (myNode.getElementType() == ElementType.FOR_STATEMENT && mySettings.ALIGN_MULTILINE_FOR && isInsideForParens(newChildIndex)) {
      Alignment prev = getUsedAlignment(newChildIndex);
      if (prev != null) {
        return new ChildAttributes(null, prev);
      }
    }

    if (newChildIndex == 0) {
      return new ChildAttributes(getCodeBlockExternalIndent(), null);
    }

    if (newChildIndex == getSubBlocks().size()) {
      return new ChildAttributes(getCodeBlockChildExternalIndent(newChildIndex), null);
    }

    return new ChildAttributes(myIndentsBefore.get(newChildIndex), null);

  }

  private boolean isInsideForParens(final int newChildIndex) {
    final List<Block> subBlocks = getSubBlocks();
    for (int i = 0; i < newChildIndex; i++) {
      if (i >= subBlocks.size()) return false;
      final Block block = subBlocks.get(i);
      if (block instanceof LeafBlock) {
        if (((LeafBlock)block).getTreeNode().getElementType() == ElementType.RPARENTH) return false;
      }
    }
    return true;
  }



}
