// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.codeInsight.editorActions.SelectWordUtil;
import com.intellij.lang.ASTNode;
import com.intellij.lexer.StringLiteralLexer;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.BasicJavaAstTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.psi.impl.source.BasicElementTypes.BASIC_STRING_LITERALS;

public class LiteralSelectioner extends AbstractBasicBackBasicSelectioner {

  @Override
  public boolean canSelect(@NotNull PsiElement e) {
    return BasicJavaAstTreeUtil.is(BasicJavaAstTreeUtil.toNode(e), BASIC_STRING_LITERALS);
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
    boolean textBlock = BasicJavaAstTreeUtil.isTextBlock(node);
    StringLiteralLexer lexer = textBlock
                               ? new StringLiteralLexer(StringLiteralLexer.NO_QUOTE_CHAR, JavaTokenType.TEXT_BLOCK_LITERAL, true, "s{")
                               : new StringLiteralLexer('"', JavaTokenType.STRING_LITERAL);
    TextRange range = node.getTextRange();
    SelectWordUtil.addWordHonoringEscapeSequences(editorText, range, cursorOffset, lexer, result);
    if (textBlock) {
      int contentStart = StringUtil.indexOf(editorText, '\n', range.getStartOffset());
      if (contentStart == -1) return result;
      int start = contentStart + 1;
      int end = range.getEndOffset();
      end -= StringUtil.endsWith(editorText, start, end, "\"\"\"") ? 4 : 1;
      for (; end >= start; end--) {
        char c = editorText.charAt(end);
        if (c == '\n' || !Character.isWhitespace(c)) {
          end += 1;
          break;
        }
      }
      if (start < end) result.add(new TextRange(start, end));
    }
    else {
      int endOffset = editorText.charAt(range.getEndOffset() - 1) == '"'
                      ? range.getEndOffset() - 1
                      : range.getEndOffset();
      result.add(new TextRange(range.getStartOffset() + 1, endOffset));
    }

    return result;
  }
}
