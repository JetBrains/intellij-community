// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.codeInsight.editorActions.SelectWordUtil;
import com.intellij.lang.ASTNode;
import com.intellij.lexer.StringLiteralLexer;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.BasicLiteralUtil;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.BasicJavaAstTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.psi.impl.source.BasicElementTypes.BASIC_STRING_LITERALS;
import static com.intellij.psi.impl.source.BasicJavaElementType.BASIC_LITERAL_EXPRESSION;

public class LiteralSelectioner extends AbstractBasicBackBasicSelectioner {

  @Override
  public boolean canSelect(@NotNull PsiElement e) {
    PsiElement parent = e.getParent();
    return isStringLiteral(e) || isStringLiteral(parent);
  }

  private static boolean isStringLiteral(PsiElement element) {
    return BasicJavaAstTreeUtil.is(BasicJavaAstTreeUtil.toNode(element), BASIC_STRING_LITERALS)
           && element.getText().startsWith("\"")
           && element.getText().endsWith("\"");
  }

  @Override
  public List<TextRange> select(@NotNull PsiElement e, @NotNull CharSequence editorText, int cursorOffset, @NotNull Editor editor) {
    List<TextRange> result = super.select(e, editorText, cursorOffset, editor);
    if (result == null) {
      return null;
    }
    ASTNode node = BasicJavaAstTreeUtil.toNode(e);
    if (node == null) {
      return null;
    }
    TextRange range = node.getTextRange();
    SelectWordUtil.addWordHonoringEscapeSequences(editorText, range, cursorOffset,
                                                  new StringLiteralLexer('\"', JavaTokenType.STRING_LITERAL),
                                                  result);
    ASTNode literalExpression = null;
    if (BasicJavaAstTreeUtil.is(node, BASIC_LITERAL_EXPRESSION)) {
      literalExpression = node;
    }
    if (literalExpression == null) {
      ASTNode parent = node.getTreeParent();
      if (BasicJavaAstTreeUtil.is(parent, BASIC_LITERAL_EXPRESSION)) {
        literalExpression = parent;
      }
    }
    PsiElement literalPsiExpression = BasicJavaAstTreeUtil.toPsi(literalExpression);
    if (literalExpression != null && literalPsiExpression != null && BasicJavaAstTreeUtil.isTextBlock(literalExpression)) {
      int contentStart = StringUtil.indexOf(editorText, '\n', range.getStartOffset());
      if (contentStart == -1) return result;
      contentStart += 1;
      int indent = BasicLiteralUtil.getTextBlockIndent(literalPsiExpression);
      if (indent == -1) return result;
      for (int i = 0; i < indent; i++) {
        if (editorText.charAt(contentStart + i) == '\n') return result;
      }
      int start = contentStart + indent;
      int end = range.getEndOffset() - 4;
      for (; end >= start; end--) {
        char c = editorText.charAt(end);
        if (c == '\n') break;
        if (!Character.isWhitespace(c)) {
          end += 1;
          break;
        }
      }
      if (start < end) result.add(new TextRange(start, end));
    }
    else {
      result.add(new TextRange(range.getStartOffset() + 1, range.getEndOffset() - 1));
    }

    return result;
  }
}
