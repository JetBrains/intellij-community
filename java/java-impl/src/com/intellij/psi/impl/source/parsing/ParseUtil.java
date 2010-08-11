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
import com.intellij.lexer.JavaLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.ParsingContext;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.impl.source.tree.java.ModifierListElement;
import com.intellij.psi.jsp.AbstractJspJavaLexer;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.SmartList;

import java.util.List;
import java.util.ListIterator;

public class ParseUtil extends ParseUtilBase {
  private ParseUtil() { }

  public static void insertMissingTokens(CompositeElement root,
                                         Lexer lexer,
                                         int startOffset,
                                         int endOffset,
                                         int state,
                                         TokenProcessor processor,
                                         ParsingContext context) {
    final MissingTokenInserter inserter;
    if (lexer instanceof JavaLexer || lexer instanceof AbstractJspJavaLexer) {
      inserter = new JavaMissingTokenInserter(root, lexer, startOffset, endOffset, state, processor, context);
    }
    else {
      inserter = new MissingTokenInserter(root, lexer, startOffset, endOffset, state, processor, context);
    }
    inserter.invoke();
  }

  public static void bindComments(final ASTNode root) {
    JavaMissingTokenInserter.bindComments(root);
  }

  private static class JavaMissingTokenInserter extends MissingTokenInserter {
    public JavaMissingTokenInserter(final CompositeElement root,
                                    final Lexer lexer,
                                    final int startOffset,
                                    final int endOffset,
                                    final int state,
                                    final TokenProcessor processor,
                                    final ParsingContext context) {
      super(root, lexer, startOffset, endOffset, state, processor, context);
    }

    @Override
    public void invoke() {
      super.invoke();
      moveEmptyImportList(myRoot);
      bindComments(myRoot);
    }

    @Override
    protected IElementType getNextTokenType() {
      return GTTokens.getTokenType(myLexer);
    }

    @Override
    protected void advanceLexer(final ASTNode next) {
      GTTokens.advance(next.getElementType(), myLexer);
    }

    protected boolean isInsertAfterElement(final TreeElement treeNext) {
      return treeNext instanceof ModifierListElement;
    }

    private static final TokenSet BEFORE_IMPORT_BIT_SET = TokenSet.orSet(ElementType.JAVA_COMMENT_OR_WHITESPACE_BIT_SET,
                                                                         TokenSet.create(JavaElementType.PACKAGE_STATEMENT));

    private static void moveEmptyImportList(final ASTNode root) {
      final TreeElement anImport = (TreeElement)TreeUtil.skipElements(root.getFirstChildNode(), BEFORE_IMPORT_BIT_SET);
      if (anImport == null || !isEmptyImportList(anImport)) return;

      final TreeElement next = (TreeElement)TreeUtil.skipElements(anImport.getTreeNext(), ElementType.JAVA_COMMENT_OR_WHITESPACE_BIT_SET);
      if (next != null && next != anImport) {
        anImport.rawRemove();
        next.rawInsertBeforeMe(anImport);
      }
    }

    private static void bindComments(ASTNode root) {
      if (TreeUtil.isLeafOrCollapsedChameleon(root)) return;

      final List<ASTNode> comments = new SmartList<ASTNode>();
      ((TreeElement)root).acceptTree(new RecursiveTreeElementWalkingVisitor(false) {
        @Override
        protected void visitNode(TreeElement child) {
          if (ElementType.JAVA_COMMENT_BIT_SET.contains(child.getElementType())) {
            comments.add(child);
          }
          if (TreeUtil.isLeafOrCollapsedChameleon(child)) return;

          super.visitNode(child);
        }
      });
      ListIterator<ASTNode> iterator;

      // we'll only bind additional preceding comments in pass 2 when the declaration does not yet have a "doc comment"
      iterator = comments.listIterator();
      while (iterator.hasNext()) {
        ASTNode comment = iterator.next();
        IElementType type = comment.getElementType();
        if (type == JavaDocElementType.DOC_COMMENT) {
          if (bindDocComment((TreeElement)comment)) iterator.remove();
        }
        // bind "trailing comments" (like "int a; // comment")
        else if (type == JavaTokenType.END_OF_LINE_COMMENT || type == JavaTokenType.C_STYLE_COMMENT) {
          if (bindTrailingComment((TreeElement)comment)) iterator.remove();
        }
      }

      // pass 2: bind preceding comments (like "// comment \n void f();")
      iterator = comments.listIterator(comments.size());
      while (iterator.hasPrevious()) {
        final ASTNode comment = iterator.previous();

        TreeElement next = (TreeElement)TreeUtil.skipElements(comment.getTreeNext(), ElementType.JAVA_WHITESPACE_BIT_SET);
        if (next != null && isEmptyImportList(next)) {
          next = (TreeElement)TreeUtil.skipElements(next.getTreeNext(), ElementType.JAVA_WHITESPACE_BIT_SET);
        }

        bindPrecedingComment((TreeElement)comment, next);
      }
    }

    private static boolean bindDocComment(TreeElement docComment) {
      TreeElement element = docComment.getTreeNext();
      if (element == null) return false;
      TreeElement startSpaces = null;

      TreeElement importList = null;
      // bypass meaningless tokens and hold'em in hands
      while (ElementType.JAVA_PLAIN_COMMENT_OR_WHITESPACE_BIT_SET.contains(element.getElementType()) ||
             isEmptyImportList(element)) {
        if (element.getElementType() == JavaElementType.IMPORT_LIST) importList = element;
        if (startSpaces == null) startSpaces = element;
        element = element.getTreeNext();
        if (element == null) return false;
      }

      if (ElementType.MEMBER_BIT_SET.contains(element.getElementType())) {
        TreeElement first = element.getFirstChildNode();
        if (startSpaces != null) {
          docComment.rawRemoveUpTo(element);
        }
        else {
          docComment.rawRemove();
        }

        first.rawInsertBeforeMe(docComment);

        if (importList != null) {
          importList.rawRemove();
          element.rawInsertBeforeMe(importList);
        }

        return true;
      }
      return false;
    }

    private static final TokenSet BIND_TRAILING_COMMENT_BIT_SET = TokenSet.orSet(
      TokenSet.create(JavaElementType.PACKAGE_STATEMENT),
      ElementType.IMPORT_STATEMENT_BASE_BIT_SET, ElementType.FULL_MEMBER_BIT_SET, ElementType.JAVA_STATEMENT_BIT_SET);

    private static boolean bindTrailingComment(TreeElement comment) {
      TreeElement element = comment.getTreePrev();
      if (element == null) return false;
      TreeElement space = null;
      if (element.getElementType() == TokenType.WHITE_SPACE) {
        space = element;
        element = element.getTreePrev();
      }
      if (element != null && BIND_TRAILING_COMMENT_BIT_SET.contains(element.getElementType())) {
        if (space == null || (!space.textContains('\n') && !space.textContains('\r'))) {
          if (!comment.textContains('\n') && !comment.textContains('\r')) {
            if (space != null) {
              space.rawRemove();
              ((CompositeElement)element).rawAddChildren(space);
            }
            comment.rawRemove();
            ((CompositeElement)element).rawAddChildren(comment);
            return true;
          }
        }
      }
      return false;
    }

    private static final TokenSet BIND_PRECEDING_COMMENT_BIT_SET = ElementType.FULL_MEMBER_BIT_SET;

    private static void bindPrecedingComment(TreeElement comment, ASTNode bindTo) {
      if (bindTo == null ||
          !BIND_PRECEDING_COMMENT_BIT_SET.contains(bindTo.getElementType()) ||
          (bindTo.getFirstChildNode() != null && bindTo.getFirstChildNode().getElementType() == JavaDocElementType.DOC_COMMENT) ||
          !isBindingComment(comment)) return;

      final TreeElement first = (TreeElement)bindTo.getFirstChildNode();
      TreeElement child = comment;
      while (child != bindTo) {
        final TreeElement next = child.getTreeNext();
        if (!isEmptyImportList(child)) {
          child.rawRemove();
          first.rawInsertBeforeMe(child);
        }
        child = next;
      }
    }

    private static boolean isBindingComment(final ASTNode comment) {
      final ASTNode prev = comment.getTreePrev();
      final boolean prevOk = prev == null ||
                             (prev.getElementType() == TokenType.WHITE_SPACE && prev.textContains('\n'));

      final ASTNode next = comment.getTreeNext();
      final boolean nextOk = next != null &&
                             (next.getElementType() != TokenType.WHITE_SPACE || StringUtil.getLineBreakCount(next.getText()) < 2);

      return prevOk && nextOk;
    }

    private static boolean isEmptyImportList(final ASTNode node) {
      return node != null && node.getElementType() == JavaElementType.IMPORT_LIST && node.getTextLength() == 0;
    }
  }
}
