// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.psi.impl.source.BasicElementTypes;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class LiteralSelectioner extends AbstractBasicBackBasicSelectioner {

  @Override
  public boolean canSelect(@NotNull PsiElement e) {
    IElementType type = e.getNode().getElementType();
    return BasicElementTypes.BASIC_STRING_LITERALS.contains(type) ||
           BasicElementTypes.BASIC_STRING_TEMPLATE_FRAGMENTS.contains(type);
  }

  @Override
  public List<TextRange> select(@NotNull PsiElement e, @NotNull CharSequence editorText, int cursorOffset, @NotNull Editor editor) {
    List<TextRange> result = super.select(e, editorText, cursorOffset, editor);
    if (result == null) return null;

    ASTNode node = e.getNode();
    StringLiteralLexer lexer = new StringLiteralLexer(StringLiteralLexer.NO_QUOTE_CHAR, node.getElementType(), true, "s{");
    SelectWordUtil.addWordHonoringEscapeSequences(editorText, node.getTextRange(), cursorOffset, lexer, result);
    result.add(getContentRange(node, editorText));
    return result;
  }

  private static TextRange getContentRange(ASTNode node, @NotNull CharSequence text) {
    final IElementType tokenType = node.getElementType();
    final TextRange range = node.getTextRange();

    if (tokenType == JavaTokenType.STRING_TEMPLATE_BEGIN || tokenType == JavaTokenType.STRING_TEMPLATE_MID ||
        tokenType == JavaTokenType.TEXT_BLOCK_TEMPLATE_MID) {
      return new TextRange(range.getStartOffset() + 1, range.getEndOffset() - 2);
    }
    else if (tokenType == JavaTokenType.STRING_TEMPLATE_END || tokenType == JavaTokenType.STRING_LITERAL) {
      int end = text.charAt(range.getEndOffset() - 1) == '"'
                      ? range.getEndOffset() - 1
                      : range.getEndOffset();
      return new TextRange(range.getStartOffset() + 1, end);
    }
    else if (tokenType == JavaTokenType.TEXT_BLOCK_TEMPLATE_BEGIN || tokenType == JavaTokenType.TEXT_BLOCK_TEMPLATE_END ||
             tokenType == JavaTokenType.TEXT_BLOCK_LITERAL) {
      int start;
      if (tokenType == JavaTokenType.TEXT_BLOCK_TEMPLATE_END) {
        start = range.getStartOffset() + 1;
      }
      else {
        start = range.getStartOffset() + 3;
        while (BasicLiteralUtil.isTextBlockWhiteSpace(text.charAt(start))) start++;
        if (text.charAt(start) == '\n') start++;
      }
      int end;
      if (tokenType == JavaTokenType.TEXT_BLOCK_TEMPLATE_BEGIN) {
        end = range.getEndOffset() - 2;
      }
      else {
        end = range.getEndOffset();
        end -= StringUtil.endsWith(text, start, end, "\"\"\"") ? 4 : 1;
        for (; end >= start; end--) {
          char c = text.charAt(end);
          if (c == '\n' || !Character.isWhitespace(c)) {
            end++;
            break;
          }
        }
      }
      return end < start ? range : new TextRange(start, end);
    }
    else {
      throw new IllegalArgumentException();
    }
  }
}