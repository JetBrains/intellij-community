/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.psi.formatter.common.AbstractBlock;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.StdTokenSets;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class CodeBlockBlock extends AbstractJavaBlock {
  private static final int BEFORE_FIRST = 0;
  private static final int BEFORE_LBRACE = 1;
  private static final int INSIDE_BODY = 2;

  private final int myChildrenIndent;

  public CodeBlockBlock(ASTNode node,
                        Wrap wrap,
                        Alignment alignment,
                        Indent indent,
                        CommonCodeStyleSettings settings,
                        JavaCodeStyleSettings javaSettings) {
    super(node, wrap, getAlignmentStrategy(alignment, node, settings), indent, settings, javaSettings);
    if (isSwitchCodeBlock() && !settings.INDENT_CASE_FROM_SWITCH) {
      myChildrenIndent = 0;
    }
    else {
      myChildrenIndent = 1;
    }
  }

  /**
   * There is a possible case that 'implements' section is incomplete (e.g. ends with comma). We may want to align lbrace
   * to the comma then.
   *
   * @param alignment     block alignment
   * @param baseNode      base AST node
   * @return              alignment strategy to use for the given node
   */
  private static AlignmentStrategy getAlignmentStrategy(Alignment alignment, ASTNode baseNode, @NotNull CommonCodeStyleSettings settings) {
    if (baseNode.getElementType() != JavaElementType.CLASS || !settings.ALIGN_MULTILINE_EXTENDS_LIST) {
      return AlignmentStrategy.wrap(alignment);
    }
    for (ASTNode node = baseNode.getLastChildNode(); node != null; node = FormatterUtil.getPreviousNonWhitespaceSibling(node)) {
      if (node.getElementType() != JavaElementType.IMPLEMENTS_LIST) {
        continue;
      }
      ASTNode lastChildNode = node.getLastChildNode();
      if (lastChildNode != null && lastChildNode.getElementType() == TokenType.ERROR_ELEMENT) {
        Alignment alignmentToUse = alignment;
        if (alignment == null) {
          alignmentToUse = Alignment.createAlignment();
        }
        return AlignmentStrategy.wrap(
          alignmentToUse, false, JavaTokenType.LBRACE, JavaElementType.JAVA_CODE_REFERENCE, node.getElementType()
        );
      }
      break;
    }
    return AlignmentStrategy.wrap(alignment);
  }

  private boolean isSwitchCodeBlock() {
    return myNode.getTreeParent().getElementType() == JavaElementType.SWITCH_STATEMENT;
  }

  @Override
  protected List<Block> buildChildren() {
    final List<Block> result = new ArrayList<>();
    Alignment childAlignment = createChildAlignment();
    Wrap childWrap = createChildWrap();

    buildChildren(result, childAlignment, childWrap);

    return result;
  }

  private void buildChildren(final List<Block> result, final Alignment childAlignment, final Wrap childWrap) {
    ASTNode child = myNode.getFirstChildNode();

    int state = BEFORE_FIRST;

    if (myNode.getPsi() instanceof PsiSyntheticClass) {
      state = INSIDE_BODY;
    }

    ChildAlignmentStrategyProvider provider = getStrategyProvider();
    while (child != null) {
      if (!FormatterUtil.containsWhiteSpacesOnly(child) && child.getTextLength() > 0) {
        AlignmentStrategy alignmentStrategy = provider.getNextChildStrategy(child);
        final Indent indent = calcCurrentIndent(child, state);
        state = calcNewState(child, state);

        if (child.getElementType() == JavaElementType.SWITCH_LABEL_STATEMENT) {
          child = processCaseAndStatementAfter(result, child, childAlignment, childWrap, indent);
        }
        else if ((myNode.getPsi() instanceof PsiClass || myNode.getPsi() instanceof PsiJavaModule) &&
                 child.getElementType() == JavaTokenType.LBRACE) {
          child = composeCodeBlock(result, child, getCodeBlockExternalIndent(), myChildrenIndent, null);
        }
        else if (myNode.getElementType() == JavaElementType.CODE_BLOCK &&
                 child.getElementType() == JavaTokenType.LBRACE &&
                 myNode.getTreeParent().getElementType() == JavaElementType.METHOD) {
          child = composeCodeBlock(result, child, indent, myChildrenIndent, childWrap);
        }
        else {
          child = processChild(result, child, alignmentStrategy, childWrap, indent);
        }
      }
      if (child != null) {
        child = child.getTreeNext();
      }
    }
  }

  @Nullable
  private ASTNode processCaseAndStatementAfter(final List<Block> result,
                                               ASTNode child,
                                               final Alignment childAlignment,
                                               final Wrap childWrap,
                                               final Indent indent) {
    final List<Block> localResult = new ArrayList<>();
    processChild(localResult, child, AlignmentStrategy.getNullStrategy(), null, Indent.getNoneIndent());
    child = child.getTreeNext();
    Indent childIndent = Indent.getNormalIndent();
    while (child != null) {
      if (child.getElementType() == JavaElementType.SWITCH_LABEL_STATEMENT || isRBrace(child)) {
        result.add(createCaseSectionBlock(localResult, childAlignment, indent, childWrap));
        return child.getTreePrev();
      }

      if (!FormatterUtil.containsWhiteSpacesOnly(child)) {

        if (child.getElementType() == JavaElementType.BLOCK_STATEMENT) {
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

  private SyntheticCodeBlock createCaseSectionBlock(final List<Block> localResult,
                                                    final Alignment childAlignment,
                                                    final Indent indent,
                                                    final Wrap childWrap) {
    final SyntheticCodeBlock result = new SyntheticCodeBlock(localResult, childAlignment, getSettings(), myJavaSettings, indent, childWrap) {
      @Override
      @NotNull
      public ChildAttributes getChildAttributes(final int newChildIndex) {
        IElementType prevElementType = null;
        if (newChildIndex > 0) {
          final Block previousBlock = getSubBlocks().get(newChildIndex - 1);
          if (previousBlock instanceof AbstractBlock) {
            prevElementType = ((AbstractBlock)previousBlock).getNode().getElementType();
          }
        }

        if (prevElementType == JavaElementType.BLOCK_STATEMENT
            || prevElementType == JavaElementType.BREAK_STATEMENT
            || prevElementType == JavaElementType.RETURN_STATEMENT) {
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
        else if (child.getElementType() == JavaTokenType.LBRACE) {
          return INSIDE_BODY;
        }
        else {
          return BEFORE_LBRACE;
        }
      }
      case BEFORE_LBRACE: {
        if (child.getElementType() == JavaTokenType.LBRACE) {
          return INSIDE_BODY;
        }
        else {
          return BEFORE_LBRACE;
        }
      }
    }
    return INSIDE_BODY;
  }

  private Indent calcCurrentIndent(final ASTNode child, final int state) {
    IElementType elementType = child.getElementType();

    if (isRBrace(child) || elementType == JavaTokenType.AT) {
      return Indent.getNoneIndent();
    }

    if (state == BEFORE_FIRST) {
      return Indent.getNoneIndent();
    }

    if (elementType == JavaElementType.SWITCH_LABEL_STATEMENT) {
      return getCodeBlockInternalIndent(myChildrenIndent);
    }

    if (state == BEFORE_LBRACE) {
      if (elementType == JavaTokenType.LBRACE
          || elementType == JavaTokenType.CLASS_KEYWORD
          || elementType == JavaTokenType.INTERFACE_KEYWORD
          || elementType == JavaTokenType.IDENTIFIER
          || elementType == JavaTokenType.ENUM_KEYWORD
          || elementType == JavaElementType.MODULE_REFERENCE) {
        return Indent.getNoneIndent();
      }
      else {
        return Indent.getContinuationIndent(myIndentSettings.USE_RELATIVE_INDENTS);
      }
    }
    else {
      return getCodeBlockInternalIndent(myChildrenIndent);
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