// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.BasicJavaAstTreeUtil;
import com.intellij.util.DocumentUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.psi.impl.source.BasicJavaElementType.*;

public final class CaseStatementsSelectioner extends AbstractBasicBackBasicSelectioner {

  @Override
  public boolean canSelect(@NotNull PsiElement e) {
    ASTNode node = BasicJavaAstTreeUtil.toNode(e);
    return node != null &&
           BasicJavaAstTreeUtil.is(node.getTreeParent(), BASIC_CODE_BLOCK) &&
           BasicJavaAstTreeUtil.is(node.getTreeParent().getTreeParent(), BASIC_SWITCH_STATEMENT);
  }

  @Override
  public List<TextRange> select(@NotNull PsiElement elementStatement,
                                @NotNull CharSequence editorText,
                                int cursorOffset,
                                @NotNull Editor editor) {
    ASTNode statement = BasicJavaAstTreeUtil.toNode(elementStatement);

    List<TextRange> result = new ArrayList<>();
    ASTNode caseStart = statement;
    ASTNode caseEnd = statement;

    if (statement == null ||
        BasicJavaAstTreeUtil.is(statement, BASIC_SWITCH_STATEMENT)) {
      return result;
    }

    ASTNode labelStatement = BasicJavaAstTreeUtil.is(statement, BASIC_SWITCH_LABEL_STATEMENT) ? statement : null;
    ASTNode sibling;
    if (labelStatement == null) {
      sibling = statement.getTreePrev();
      while (sibling != null && !BasicJavaAstTreeUtil.is(sibling, BASIC_SWITCH_LABEL_STATEMENT)) {
        if (!BasicJavaAstTreeUtil.isWhiteSpace(sibling)) caseStart = sibling;
        sibling = sibling.getTreePrev();
      }
      labelStatement = sibling;
    }
    if (labelStatement != null) {
      ASTNode nextLabel = BasicJavaAstTreeUtil.skipSiblingsBackward(labelStatement, TokenType.WHITE_SPACE);
      while (BasicJavaAstTreeUtil.is(nextLabel, BASIC_SWITCH_LABEL_STATEMENT)) {
        labelStatement = nextLabel;
        nextLabel = BasicJavaAstTreeUtil.skipSiblingsBackward(labelStatement, TokenType.WHITE_SPACE);
      }
    }

    sibling = BasicJavaAstTreeUtil.isWhiteSpace(statement) ? statement.getTreeNext() : statement;
    while (BasicJavaAstTreeUtil.is(sibling, BASIC_SWITCH_LABEL_STATEMENT)) {
      sibling = BasicJavaAstTreeUtil.skipSiblingsForward(sibling, TokenType.WHITE_SPACE);
    }
    while (sibling != null && !BasicJavaAstTreeUtil.is(sibling, BASIC_SWITCH_LABEL_STATEMENT)) {
      if (!BasicJavaAstTreeUtil.isWhiteSpace(sibling) &&
          !BasicJavaAstTreeUtil.isJavaToken(sibling) // end of switch
      ) {
        caseEnd = sibling;
      }
      sibling = sibling.getTreeNext();
    }

    Document document = editor.getDocument();

    int endOffset =
      DocumentUtil.getLineEndOffset(BasicJavaAstTreeUtil.getTextOffset(caseEnd) + caseEnd.getTextLength(), document) + 1;

    if (!BasicJavaAstTreeUtil.is(caseStart, BASIC_SWITCH_LABEL_STATEMENT)) {
      result.add(new TextRange(DocumentUtil.getLineStartOffset(BasicJavaAstTreeUtil.getTextOffset(caseStart), document),
                               endOffset));
    }
    if (labelStatement != null) {
      result.add(
        new TextRange(DocumentUtil.getLineStartOffset(BasicJavaAstTreeUtil.getTextOffset(labelStatement), document),
                      endOffset));
    }
    return result;
  }
}
