// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.BasicJavaAstTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.psi.impl.source.BasicJavaElementType.*;

public final class StatementGroupSelectioner extends AbstractBasicBackBasicSelectioner {
  @Override
  public boolean canSelect(@NotNull PsiElement e) {
    return BasicJavaAstTreeUtil.is(BasicJavaAstTreeUtil.toNode(e), STATEMENT_SET);
  }

  @Override
  public List<TextRange> select(@NotNull PsiElement e, @NotNull CharSequence editorText, int cursorOffset, @NotNull Editor editor) {
    List<TextRange> result = new ArrayList<>();

    PsiElement parentElement = e.getParent();
    ASTNode node = BasicJavaAstTreeUtil.toNode(e);
    if (node == null) {
      return result;
    }
    ASTNode parentNode = BasicJavaAstTreeUtil.toNode(parentElement);
    if (!BasicJavaAstTreeUtil.is(parentNode, BASIC_CODE_BLOCK) &&
        !BasicJavaAstTreeUtil.is(parentNode, BASIC_BLOCK_STATEMENT) ||
        BasicJavaAstTreeUtil.is(node, BASIC_SWITCH_LABEL_STATEMENT)) {
      return result;
    }


    ASTNode startElement = node;
    ASTNode endElement = node;


    while (startElement.getTreePrev() != null) {
      ASTNode sibling = startElement.getTreePrev();

      if (BasicJavaAstTreeUtil.is(sibling, JavaTokenType.LBRACE)) break;

      if (BasicJavaAstTreeUtil.isWhiteSpace(sibling)) {
        String[] strings = LineTokenizer.tokenize(sibling.getText().toCharArray(), false);
        if (strings.length > 2) {
          break;
        }
      }

      if (BasicJavaAstTreeUtil.is(sibling, BASIC_SWITCH_LABEL_STATEMENT)) break;

      startElement = sibling;
    }

    while (BasicJavaAstTreeUtil.isWhiteSpace(startElement)) {
      startElement = startElement.getTreeNext();
    }

    while (endElement.getTreeNext() != null) {
      ASTNode sibling = endElement.getTreeNext();

      if (BasicJavaAstTreeUtil.is(sibling, JavaTokenType.RBRACE)) break;

      if (BasicJavaAstTreeUtil.isWhiteSpace(sibling)) {
        String[] strings = LineTokenizer.tokenize(sibling.getText().toCharArray(), false);
        if (strings.length > 2) {
          break;
        }
      }

      if (BasicJavaAstTreeUtil.is(sibling, BASIC_SWITCH_LABEL_STATEMENT)) break;

      endElement = sibling;
    }

    while (BasicJavaAstTreeUtil.isWhiteSpace(endElement)) {
      endElement = endElement.getTreePrev();
    }

    result.addAll(expandToWholeLine(editorText, new TextRange(startElement.getTextRange().getStartOffset(),
                                                              endElement.getTextRange().getEndOffset())));

    return result;
  }
}
