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
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.FormatterUtil;
import com.intellij.psi.formatter.common.AbstractBlock;
import com.intellij.formatting.alignment.AlignmentStrategy;
import com.intellij.psi.impl.source.jsp.jspJava.JspClass;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.StdTokenSets;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class CodeBlockBlock extends AbstractJavaBlock {
  private final static int BEFORE_FIRST = 0;
  private final static int BEFORE_LBRACE = 1;
  private final static int INSIDE_BODY = 2;

  private final int myChildrenIndent;

  public CodeBlockBlock(final ASTNode node,
                        final Wrap wrap,
                        final Alignment alignment,
                        final Indent indent,
                        final CodeStyleSettings settings) {
    super(node, wrap, alignment, indent, settings);
    if (isSwitchCodeBlock() && !settings.INDENT_CASE_FROM_SWITCH) {
      myChildrenIndent = 0;
    }
    else {
      myChildrenIndent = 1;
    }
  }

  private boolean isSwitchCodeBlock() {
    return myNode.getTreeParent().getElementType() == ElementType.SWITCH_STATEMENT;
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

    int state = BEFORE_FIRST;

    if (myNode.getPsi() instanceof JspClass) {
      state = INSIDE_BODY;
    }

    while (child != null) {
      if (!FormatterUtil.containsWhiteSpacesOnly(child) && child.getTextLength() > 0) {
        final Indent indent = calcCurrentIndent(child, state);
        state = calcNewState(child, state);

        if (child.getElementType() == ElementType.SWITCH_LABEL_STATEMENT) {
          child = processCaseAndStatementAfter(result, child, childAlignment, childWrap, indent);
        }
        else if (myNode.getElementType() == ElementType.CLASS && child.getElementType() == ElementType.LBRACE) {
          child = composeCodeBlock(result, child, getCodeBlockExternalIndent(), myChildrenIndent);
        }
        else if (myNode.getElementType() == ElementType.CODE_BLOCK && child.getElementType() == ElementType.LBRACE
                 && myNode.getTreeParent().getElementType() == JavaElementType.METHOD)
        {
          child = composeCodeBlock(result, child, indent, myChildrenIndent);
        }
        else {
          child = processChild(result, child, childAlignment, childWrap, indent);
        }
      }
      if (child != null) {
        child = child.getTreeNext();
      }
    }
  }

  @Nullable
  private ASTNode processCaseAndStatementAfter(final ArrayList<Block> result,
                                               ASTNode child,
                                               final Alignment childAlignment,
                                               final Wrap childWrap, final Indent indent) {
    final ArrayList<Block> localResult = new ArrayList<Block>();
    processChild(localResult, child, AlignmentStrategy.getNullStrategy(), null, Indent.getNoneIndent());
    child = child.getTreeNext();
    Indent childIndent = Indent.getNormalIndent();
    while (child != null) {
      if (child.getElementType() == ElementType.SWITCH_LABEL_STATEMENT || isRBrace(child)) {
        result.add(createCaseSectionBlock(localResult, childAlignment, indent, childWrap));
        return child.getTreePrev();
      }

      if (!FormatterUtil.containsWhiteSpacesOnly(child)) {

        if (child.getElementType() == ElementType.BLOCK_STATEMENT) {
          childIndent = Indent.getNoneIndent();
        }

        boolean breakOrReturn = isBreakOrReturn(child);
        processChild(localResult, child, AlignmentStrategy.getNullStrategy(), null, childIndent);
        if (breakOrReturn) {
          result.add(createCaseSectionBlock(localResult, childAlignment, indent, childWrap));
          return child;
        }
      }

      child = child.getTreeNext();
    }
    result.add(createCaseSectionBlock(localResult, childAlignment, indent, childWrap));
    return null;
  }

  private static boolean isBreakOrReturn(final ASTNode child) {
    IElementType elementType = child.getElementType();
    return JavaElementType.BREAK_STATEMENT == elementType || JavaElementType.RETURN_STATEMENT == elementType;
  }

  private SyntheticCodeBlock createCaseSectionBlock(final ArrayList<Block> localResult, final Alignment childAlignment, final Indent indent,
                                                    final Wrap childWrap) {
    final SyntheticCodeBlock result = new SyntheticCodeBlock(localResult, childAlignment, getSettings(), indent, childWrap) {
      @NotNull
      public ChildAttributes getChildAttributes(final int newChildIndex) {
        IElementType prevElementType = null;
        if (newChildIndex > 0) {
          final Block previousBlock = getSubBlocks().get(newChildIndex - 1);
          if (previousBlock instanceof AbstractBlock) {
            prevElementType = ((AbstractBlock)previousBlock).getNode().getElementType();
          }
        }

        if (prevElementType == ElementType.BLOCK_STATEMENT
            || prevElementType == ElementType.BREAK_STATEMENT
            || prevElementType == ElementType.RETURN_STATEMENT) {
          return new ChildAttributes(Indent.getNoneIndent(), null);
        }
        else {
          return super.getChildAttributes(newChildIndex);
        }
      }

    };
    result.setChildAttributes(new ChildAttributes(Indent.getNormalIndent(), null));
    result.setIsIncomplete(true);
    return result;
  }

  private static int calcNewState(final ASTNode child, int state) {
    switch (state) {
      case BEFORE_FIRST: {
        if (StdTokenSets.COMMENT_BIT_SET.contains(child.getElementType())) {
          return BEFORE_FIRST;
        }
        else if (isLBrace(child)) {
          return INSIDE_BODY;
        }
        else {
          return BEFORE_LBRACE;
        }
      }
      case BEFORE_LBRACE: {
        if (isLBrace(child)) {
          return INSIDE_BODY;
        }
        else {
          return BEFORE_LBRACE;
        }
      }
    }
    return INSIDE_BODY;
  }

  private static boolean isLBrace(final ASTNode child) {
    return child.getElementType() == ElementType.LBRACE;
  }

  private Indent calcCurrentIndent(final ASTNode child, final int state) {
    if (isRBrace(child)) {
      return Indent.getNoneIndent();
    }

    if (state == BEFORE_FIRST) return Indent.getNoneIndent();

    if (child.getElementType() == ElementType.SWITCH_LABEL_STATEMENT) {
      return getCodeBlockInternalIndent(myChildrenIndent);
    }
    if (state == BEFORE_LBRACE) {
      if (isLBrace(child)) {
        return Indent.getNoneIndent();
      }
      else {
        return Indent.getContinuationIndent();
      }
    }
    else {
      if (isRBrace(child)) {
        return Indent.getNoneIndent();
      }
      else {
        return getCodeBlockInternalIndent(myChildrenIndent);
      }
    }
  }

  @Override
  @NotNull
  public ChildAttributes getChildAttributes(final int newChildIndex) {
    if (isAfter(newChildIndex, new IElementType[]{JavaDocElementType.DOC_COMMENT, JavaElementType.MODIFIER_LIST})) {
      return new ChildAttributes(Indent.getNoneIndent(), null);
    }
    else {
      if (getSubBlocks().size() == newChildIndex) {
        return new ChildAttributes(Indent.getNoneIndent(), null);
      }
      else {
        return new ChildAttributes(getCodeBlockInternalIndent(myChildrenIndent), null);
      }
    }
  }
}
