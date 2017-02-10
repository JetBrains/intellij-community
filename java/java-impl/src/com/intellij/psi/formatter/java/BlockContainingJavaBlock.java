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
import com.intellij.formatting.alignment.AlignmentStrategy;
import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.formatter.FormatterUtil;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.StdTokenSets;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class BlockContainingJavaBlock extends AbstractJavaBlock{

  private static final TokenSet TYPES_OF_STATEMENTS_WITH_OPTIONAL_BRACES = TokenSet.create(
    JavaElementType.IF_STATEMENT, JavaElementType.WHILE_STATEMENT, JavaElementType.FOR_STATEMENT
  );
  
  private static final int BEFORE_FIRST = 0;
  private static final int BEFORE_BLOCK = 1;
  private static final int AFTER_ELSE = 2;

  private final List<Indent> myIndentsBefore = new ArrayList<>();

  public BlockContainingJavaBlock(ASTNode node,
                                  Wrap wrap,
                                  Alignment alignment,
                                  Indent indent,
                                  CommonCodeStyleSettings settings,
                                  JavaCodeStyleSettings javaSettings) {
    super(node, wrap, alignment, indent, settings, javaSettings);
  }

  public BlockContainingJavaBlock(ASTNode child, Indent indent, AlignmentStrategy strategy, CommonCodeStyleSettings settings, JavaCodeStyleSettings javaSettings) {
    super(child, null, strategy, indent, settings, javaSettings);
  }

  @Override
  protected List<Block> buildChildren() {
    final ArrayList<Block> result = new ArrayList<>();
    Alignment childAlignment = createChildAlignment();
    Wrap childWrap = createChildWrap();

    buildChildren(result, childAlignment, childWrap);

    for (Block block : result) {
      if (block instanceof AbstractJavaBlock) {
        ((AbstractJavaBlock)block).setParentBlock(this);
      }
    }

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
        if (prevChild != null && myNode.getElementType() == JavaElementType.METHOD
            && ElementType.JAVA_COMMENT_BIT_SET.contains(prevChild.getElementType())
            && !ElementType.JAVA_COMMENT_BIT_SET.contains(child.getElementType()))
        {
          prevChild = child;
          child = composeCodeBlock(result, child, Indent.getNoneIndent(), 0, null);
        }
        else {
          prevChild = child;
          Alignment simpleMethodBraceAlignment = myAlignmentStrategy.getAlignment(child.getElementType());
          Alignment toUse = childAlignment != null ? childAlignment : simpleMethodBraceAlignment;
          child = processChild(result, child, toUse, childWrap, indent);
        }                
        for (int i = myIndentsBefore.size(); i < result.size(); i++) {
          myIndentsBefore.add(Indent.getContinuationIndent(myIndentSettings.USE_RELATIVE_INDENTS));
        }
      }
      if (child != null) {
        child = child.getTreeNext();
      }
    }
  }

  private static int calcNewState(final ASTNode child, final int state) {
    if (state == BEFORE_FIRST) {
      if (child.getElementType() == JavaTokenType.ELSE_KEYWORD) {
        return AFTER_ELSE;
      }
      if (StdTokenSets.COMMENT_BIT_SET.contains(child.getElementType())) {
        return BEFORE_FIRST;
      }
      if (child.getElementType() == JavaElementType.CATCH_SECTION) {
        return BEFORE_FIRST;
      }
    } else if (state == BEFORE_BLOCK){
      if (child.getElementType() == JavaTokenType.ELSE_KEYWORD) {
        return AFTER_ELSE;
      }
      if (child.getElementType() == JavaElementType.BLOCK_STATEMENT) {
        return BEFORE_FIRST;
      }
      if (child.getElementType() == JavaElementType.CODE_BLOCK) {
        return BEFORE_FIRST;
      }

    }
    return BEFORE_BLOCK;
  }

  private Indent calcIndent(final ASTNode child, final int state) {
    if (state == AFTER_ELSE && child.getElementType() == JavaElementType.IF_STATEMENT) {
      if (mySettings.SPECIAL_ELSE_IF_TREATMENT) {
        return Indent.getNoneIndent();
      } else {
        return getCodeBlockInternalIndent(1);
      }
    }
    if (isSimpleStatement(child)){
      return createNormalIndent(1);
    }
    if (child.getElementType() == JavaTokenType.ELSE_KEYWORD)
      return Indent.getNoneIndent();
    if (state == BEFORE_FIRST || child.getElementType() == JavaTokenType.WHILE_KEYWORD) {
      return Indent.getNoneIndent();
    }
    else {
      if (isPartOfCodeBlock(child)) {
        return getCodeBlockExternalIndent();
      }
      else if (isSimpleStatement(child) || StdTokenSets.COMMENT_BIT_SET.contains(child.getElementType())){
        return getCodeBlockInternalIndent(1);
      }
      else if (isNodeParentMethod(child) && 
               (child.getElementType() == JavaElementType.TYPE
                || child.getElementType() == JavaTokenType.IDENTIFIER
                || child.getElementType() == JavaElementType.THROWS_LIST
                || child.getElementType() == JavaElementType.TYPE_PARAMETER_LIST)) {
        return Indent.getNoneIndent();
      }
      else {
        return Indent.getContinuationIndent(myIndentSettings.USE_RELATIVE_INDENTS);
      }
    }
  }

  private static boolean isNodeParentMethod(@NotNull ASTNode node) {
    return node.getTreeParent() != null && node.getTreeParent().getElementType() == JavaElementType.METHOD;
  }

  private Indent calcIndentBefore(final ASTNode child, final int state) {
    if (state == AFTER_ELSE) {
      if (!mySettings.SPECIAL_ELSE_IF_TREATMENT) {
        return getCodeBlockInternalIndent(1);
      } else {
        return getCodeBlockExternalIndent();
      }
    }
    if (state == BEFORE_BLOCK && (isSimpleStatement(child) || child.getElementType() == JavaElementType.BLOCK_STATEMENT)){
      return getCodeBlockInternalIndent(0);
    }
    if (state == BEFORE_FIRST) {
      return getCodeBlockExternalIndent();
    }
    if (child.getElementType() == JavaTokenType.ELSE_KEYWORD) {
      return getCodeBlockExternalIndent();
    }
    if (child.getPsi() instanceof PsiTypeElement) {
      return Indent.getNoneIndent();
    }

    return Indent.getContinuationIndent(myIndentSettings.USE_RELATIVE_INDENTS);
  }

  private static boolean isSimpleStatement(final ASTNode child) {
    if (child.getElementType() == JavaElementType.BLOCK_STATEMENT) return false;
    if (!(child.getPsi() instanceof PsiStatement)) return false;
    return isStatement(child, child.getTreeParent());
  }

  private static boolean isPartOfCodeBlock(final ASTNode child) {
    if (child == null) return false;
    if (child.getElementType() == JavaElementType.BLOCK_STATEMENT) return true;
    if (child.getElementType() == JavaElementType.CODE_BLOCK) return true;

    if (FormatterUtil.containsWhiteSpacesOnly(child)) return isPartOfCodeBlock(child.getTreeNext());
    if (child.getElementType() == JavaTokenType.END_OF_LINE_COMMENT) return isPartOfCodeBlock(child.getTreeNext());
    return child.getElementType() == JavaDocElementType.DOC_COMMENT;
  }



  @Override
  @NotNull
  public ChildAttributes getChildAttributes(final int newChildIndex) {
    if (isAfter(newChildIndex, new IElementType[]{JavaDocElementType.DOC_COMMENT})) {
      return new ChildAttributes(Indent.getNoneIndent(), null);
    }

    if (myNode.getElementType() == JavaElementType.FOR_STATEMENT && mySettings.ALIGN_MULTILINE_FOR && isInsideForParens(newChildIndex)) {
      Alignment prev = getUsedAlignment(newChildIndex);
      if (prev != null) {
        return new ChildAttributes(null, prev);
      }
    }

    if (newChildIndex == 0) {
      return new ChildAttributes(getCodeBlockExternalIndent(), null);
    }

    boolean useExternalIndent = false;
    if (newChildIndex == getSubBlocks().size()) {
      useExternalIndent = true;
    }
    else if (TYPES_OF_STATEMENTS_WITH_OPTIONAL_BRACES.contains(myNode.getElementType())) {
      // There is a possible case that we have situation like below:
      //    if (true) <enter was pressed here>
      //    <caret>
      //    System.out.println();
      // We would like to indent current caret position then because there is a high probability that the user starts
      // typing there (populating statement body). So, we perform dedicated check for that here and use 'external indent'
      // if necessary.
      Block prevBlock = getSubBlocks().get(newChildIndex - 1);
      Block nextBlock = getSubBlocks().get(newChildIndex);
      if (prevBlock instanceof ASTBlock && nextBlock instanceof ASTBlock) {
        ASTNode prevNode = ((ASTBlock)prevBlock).getNode();
        ASTNode nextNode = ((ASTBlock)nextBlock).getNode();
        if (prevNode != null && nextNode != null && prevNode.getElementType() == JavaTokenType.RPARENTH
            && nextNode.getElementType() != JavaTokenType.LBRACE)
        {
          useExternalIndent = true;
        }
      }
    }
    
    if (useExternalIndent) {
      return new ChildAttributes(getCodeBlockChildExternalIndent(newChildIndex), null);
    }
    else {
      return new ChildAttributes(myIndentsBefore.get(newChildIndex), getUsedAlignment(newChildIndex));
    }
  }

  private boolean isInsideForParens(final int newChildIndex) {
    final List<Block> subBlocks = getSubBlocks();
    for (int i = 0; i < newChildIndex; i++) {
      if (i >= subBlocks.size()) return false;
      final Block block = subBlocks.get(i);
      if (block instanceof LeafBlock) {
        if (((LeafBlock)block).getTreeNode().getElementType() == JavaTokenType.RPARENTH) return false;
      }
    }
    return true;
  }



}
