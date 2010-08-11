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

package com.intellij.psi.impl.source.parsing;

import com.intellij.lang.ASTNode;
import com.intellij.lexer.Lexer;
import com.intellij.psi.impl.source.ParsingContext;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ILazyParseableElementType;

public class MissingTokenInserter {
  protected final CompositeElement myRoot;
  protected final Lexer myLexer;
  private final int myStartOffset;
  private final int myEndOffset;
  private final int myState;
  private final TokenProcessor myProcessor;
  private final ParsingContext myContext;

  public MissingTokenInserter(final CompositeElement root,
                              final Lexer lexer,
                              final int startOffset,
                              final int endOffset,
                              final int state,
                              final TokenProcessor processor,
                              final ParsingContext context) {
    myRoot = root;
    myLexer = lexer;
    myStartOffset = startOffset;
    myEndOffset = endOffset;
    myState = state;
    myProcessor = processor;
    myContext = context;
  }

  public void invoke() {
    if (myState < 0) {
      myLexer.start(myLexer.getBufferSequence(), myStartOffset, myEndOffset);
    }
    else {
      myLexer.start(myLexer.getBufferSequence(), myStartOffset, myEndOffset, myState);
    }

    TreeElement leaf = TreeUtil.findFirstLeafOrChameleon(myRoot);
    if (leaf == null) {
      final TreeElement firstMissing = myProcessor.process(myLexer, myContext);
      if (firstMissing != null) {
        myRoot.rawAddChildren(firstMissing);
      }
      return;
    }
    else {
      // Missing in the beginning
      final IElementType tokenType = getNextTokenType();
      if (tokenType != leaf.getElementType() && myProcessor.isTokenValid(tokenType)) {
        final TreeElement firstMissing = myProcessor.process(myLexer, myContext);
        if (firstMissing != null) {
          myRoot.getFirstChildNode().rawInsertBeforeMe(firstMissing);
        }
      }
      passTokenOrChameleon(leaf);
    }
    // Missing in tree body
    insertMissingTokensInTreeBody(leaf);
    if (myLexer.getTokenType() != null) {
      // whitespaces at the end of the file
      final TreeElement firstMissing = myProcessor.process(myLexer, myContext);
      if (firstMissing != null) {
        ASTNode current = myRoot;
        while (current instanceof CompositeElement) {
          if (current.getUserData(TreeUtil.UNCLOSED_ELEMENT_PROPERTY) != null) break;
          current = current.getLastChildNode();
        }
        if (current instanceof CompositeElement) {
          ((CompositeElement)current).rawAddChildren(firstMissing);
        }
        else {
          myRoot.getLastChildNode().rawInsertAfterMe(firstMissing);
        }
      }
    }
  }

  protected IElementType getNextTokenType() {
    return myLexer.getTokenType();
  }

  private void insertMissingTokensInTreeBody(TreeElement leaf) {
    final TreeUtil.CommonParentState commonParents = new TreeUtil.CommonParentState();
    while (leaf != null) {
      commonParents.strongWhiteSpaceHolder = null;
      final IElementType tokenType = getNextTokenType();
      final TreeElement next = TreeUtil.nextLeaf(leaf, commonParents, tokenType instanceof ILazyParseableElementType ? tokenType : null, false);

      if (next == null || tokenType == null) break;
      if (tokenType != next.getElementType() && myProcessor.isTokenValid(tokenType)) {
        final TreeElement firstMissing = myProcessor.process(myLexer, myContext);
        final CompositeElement unclosedElement = commonParents.strongWhiteSpaceHolder;
        if (unclosedElement != null) {
          if (commonParents.isStrongElementOnRisingSlope || unclosedElement.getFirstChildNode() == null) {
            unclosedElement.rawAddChildren(firstMissing);
          }
          else {
            unclosedElement.getFirstChildNode().rawInsertBeforeMe(firstMissing);
          }
        }
        else {
          final ASTNode insertBefore = commonParents.nextLeafBranchStart;
          TreeElement insertAfter = commonParents.startLeafBranchStart;
          TreeElement current = commonParents.startLeafBranchStart;
          while (current != insertBefore) {
            final TreeElement treeNext = current.getTreeNext();
            if (treeNext == insertBefore) {
              insertAfter = current;
              break;
            }
            if (isInsertAfterElement(treeNext)) {
              insertAfter = current;
              break;
            }
            if (treeNext.getUserData(TreeUtil.UNCLOSED_ELEMENT_PROPERTY) != null) {
              insertAfter = null;
              ((CompositeElement)treeNext).rawAddChildren(firstMissing);
              break;
            }
            current = treeNext;
          }
          if (insertAfter != null) insertAfter.rawInsertAfterMe(firstMissing);
        }
      }
      passTokenOrChameleon(next);
      leaf = next;
    }
  }

  protected boolean isInsertAfterElement(TreeElement treeNext) {
    return false;
  }

  private void passTokenOrChameleon(final ASTNode node) {
    if (node instanceof LeafElement && node instanceof OuterLanguageElement
        || TreeUtil.isCollapsedChameleon(node)) {
      final int endOfChameleon = node.getTextLength() + myLexer.getTokenStart();
      while (myLexer.getTokenType() != null && myLexer.getTokenEnd() < endOfChameleon) {
        myLexer.advance();
      }
    }
    advanceLexer(node);
  }

  protected void advanceLexer(final ASTNode next) {
    myLexer.advance();
  }
}
