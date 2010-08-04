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

import com.intellij.lang.ASTFactory;
import com.intellij.lang.ASTNode;
import com.intellij.lexer.JavaLexer;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.LexerUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.ParsingContext;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.impl.source.tree.java.ModifierListElement;
import com.intellij.psi.jsp.AbstractJspJavaLexer;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.CharTable;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.List;

public class ParseUtil {
  private ParseUtil() { }

  @Nullable
  public static TreeElement createTokenElement(Lexer lexer, CharTable table) {
    IElementType tokenType = lexer.getTokenType();
    if (tokenType == null) return null;
    if (tokenType == JavaDocElementType.DOC_COMMENT) {
      return ASTFactory.lazy(JavaDocElementType.DOC_COMMENT, LexerUtil.internToken(lexer, table));
    }
    else {
      return ASTFactory.leaf(tokenType, LexerUtil.internToken(lexer, table));
    }
  }

  /*public static void insertMissingTokens(CompositeElement root,
                                         Lexer lexer,
                                         int startOffset,
                                         int endOffset,
                                         final int state, TokenProcessor processor, ParsingContext context) {
    insertMissingTokens(root, lexer, startOffset, endOffset, -1, processor, context);
  }*/

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


  private static class JavaMissingTokenInserter extends MissingTokenInserter {

    public JavaMissingTokenInserter(final CompositeElement root, final Lexer lexer, final int startOffset, final int endOffset, final int state,
                                    final TokenProcessor processor,
                                    final ParsingContext context) {
      super(root, lexer, startOffset, endOffset, state, processor, context);
    }

    @Override
    public void invoke() {
      super.invoke();
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

    private static void bindComments(ASTNode root) {
      if (TreeUtil.isLeafOrCollapsedChameleon(root)) return;

      final List<ASTNode> comments = new SmartList<ASTNode>();
      ((TreeElement)root).acceptTree(new RecursiveTreeElementWalkingVisitor(false) {
        @Override
        protected void visitNode(TreeElement child) {
          IElementType type = child.getElementType();
          if (type == JavaDocElementType.DOC_COMMENT ||
              type == JavaTokenType.END_OF_LINE_COMMENT ||
              type == JavaTokenType.C_STYLE_COMMENT) {
            comments.add(child);
          }
          if (TreeUtil.isLeafOrCollapsedChameleon(child)) return;

          super.visitNode(child);
        }
      });

      // we'll only bind additional preceding comments in pass 2 when the declaration does not yet have a "doc comment"
      boolean docCommentBound = false;

      Iterator<ASTNode> iterator = comments.iterator();
      while (iterator.hasNext()) {
        ASTNode child = iterator.next();
        IElementType type = child.getElementType();
        if (type == JavaDocElementType.DOC_COMMENT) {
          if (bindDocComment((TreeElement)child)) {
            iterator.remove();
            docCommentBound = true;
          }
        }
        // bind "trailing comments" (like "int a; // comment")
        else if (type == JavaTokenType.END_OF_LINE_COMMENT || type == JavaTokenType.C_STYLE_COMMENT) {
          if (bindTrailingComment((TreeElement)child)) iterator.remove();
        }
      }

      //pass 2: bind preceding comments (like "// comment \n void f();")
      if (!docCommentBound) {
        for (ASTNode child : comments) {
          if (child.getElementType() == JavaTokenType.END_OF_LINE_COMMENT || child.getElementType() == JavaTokenType.C_STYLE_COMMENT) {
            TreeElement next = (TreeElement)TreeUtil.skipElements(child, PRECEDING_COMMENT_OR_SPACE_BIT_SET);
            bindPrecedingComment((TreeElement)child, next);
          }
        }
      }
    }

    /*
    private static void bindComments(ASTNode root) {
      TreeElement child = (TreeElement)root.getFirstChildNode();
      while (child != null) {
        if (child.getElementType() == JavaDocElementType.DOC_COMMENT) {
          if (bindDocComment(child)) {
            child = child.getTreeParent();
            continue;
          }
        }

        // bind "trailing comments" (like "int a; // comment")
        if (child.getElementType() == JavaTokenType.END_OF_LINE_COMMENT || child.getElementType() == JavaTokenType.C_STYLE_COMMENT) {
          if (bindTrailingComment(child)) {
            child = child.getTreeParent();
            continue;
          }
        }

        bindComments(child);
        child = child.getTreeNext();
      }

      //pass 2: bind preceding comments (like "// comment \n void f();")
      child = (TreeElement)root.getFirstChildNode();
      while(child != null) {
        if (child.getElementType() == JavaTokenType.END_OF_LINE_COMMENT || child.getElementType() == JavaTokenType.C_STYLE_COMMENT) {
          TreeElement next = (TreeElement)TreeUtil.skipElements(child, PRECEDING_COMMENT_OR_SPACE_BIT_SET);
          bindPrecedingComment(child, next);
          child = next;
        } else {
          child = child.getTreeNext();
        }
      }
    }
    */

    private static boolean bindDocComment(TreeElement docComment) {
      TreeElement element = docComment.getTreeNext();
      if (element == null) return false;
      TreeElement startSpaces = null;

      TreeElement importList = null;
      // Bypass meaningless tokens and hold'em in hands
      while (element.getElementType() == TokenType.WHITE_SPACE ||
             element.getElementType() == JavaTokenType.C_STYLE_COMMENT ||
             element.getElementType() == JavaTokenType.END_OF_LINE_COMMENT ||
             element.getElementType() == JavaElementType.IMPORT_LIST && element.getTextLength() == 0) {
        if (element.getElementType() == JavaElementType.IMPORT_LIST) importList = element;
        if (startSpaces == null) startSpaces = element;
        element = element.getTreeNext();
        if (element == null) return false;
      }

      if (element.getElementType() == JavaElementType.CLASS ||
          element.getElementType() == JavaElementType.FIELD ||
          element.getElementType() == JavaElementType.METHOD ||
          element.getElementType() == JavaElementType.ENUM_CONSTANT ||
          element.getElementType() == JavaElementType.ANNOTATION_METHOD) {
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
      TokenSet.create(JavaElementType.FIELD, JavaElementType.METHOD, JavaElementType.CLASS, JavaElementType.CLASS_INITIALIZER,
                      JavaElementType.IMPORT_STATEMENT, JavaElementType.IMPORT_STATIC_STATEMENT, JavaElementType.PACKAGE_STATEMENT),
      ElementType.JAVA_STATEMENT_BIT_SET);

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

    private static final TokenSet BIND_PRECEDING_COMMENT_BIT_SET = TokenSet.create(JavaElementType.FIELD, JavaElementType.METHOD,
                                                                                   JavaElementType.CLASS, JavaElementType.CLASS_INITIALIZER);

    private static final TokenSet PRECEDING_COMMENT_OR_SPACE_BIT_SET = TokenSet.create(JavaTokenType.C_STYLE_COMMENT,
                                                                                       JavaTokenType.END_OF_LINE_COMMENT,
                                                                                       JavaDocElementType.DOC_COMMENT,
                                                                                       TokenType.WHITE_SPACE);

    private static void bindPrecedingComment(TreeElement comment, ASTNode bindTo) {
      if (bindTo == null || bindTo.getFirstChildNode() != null && bindTo.getFirstChildNode().getElementType() == JavaDocElementType.DOC_COMMENT) return;

      if (bindTo.getElementType() == JavaElementType.IMPORT_LIST && bindTo.getTextLength() == 0) {
        bindTo = bindTo.getTreeNext();
      }

      ASTNode toStart = isBindingComment(comment) ? comment : null;
      if (bindTo != null && BIND_PRECEDING_COMMENT_BIT_SET.contains(bindTo.getElementType())) {
        for (ASTNode child = comment; child != bindTo; child = child.getTreeNext()) {
          if (child.getElementType() == TokenType.WHITE_SPACE) {
            int count = StringUtil.getLineBreakCount(child.getText());
            if (count > 1) toStart = null;
          }
          else {
            if (child.getTreePrev() != null && child.getTreePrev().getElementType() == TokenType.WHITE_SPACE) {
              LeafElement prev = (LeafElement)child.getTreePrev();
              char lastC = prev.charAt(prev.getTextLength() - 1);
              if (lastC == '\n' || lastC == '\r') toStart = isBindingComment(child) ? child : null;
            }
            else {
              return;
            }
          }
        }

        if (toStart == null) return;

        TreeElement first = (TreeElement)bindTo.getFirstChildNode();
        TreeElement child = (TreeElement)toStart;
        while (child != bindTo) {
          TreeElement next = child.getTreeNext();
          if (child.getElementType() != JavaElementType.IMPORT_LIST) {
            child.rawRemove();
            first.rawInsertBeforeMe(child);
          }
          child = next;
        }
      }
    }

    private static boolean isBindingComment(final ASTNode node) {
      ASTNode prev = node.getTreePrev();
      if (prev != null) {
        if (prev.getElementType() != TokenType.WHITE_SPACE) {
          return false;
        }
        else {
          if (!prev.textContains('\n')) return false;
        }
      }

      return true;
    }

    protected boolean isInsertAfterElement(final TreeElement treeNext) {
      return treeNext instanceof ModifierListElement;
    }
  }
}
