// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.java.PsiFragmentImpl;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

public final class TextBlockJoinLinesHandler implements JoinRawLinesHandlerDelegate {
  @Override
  public int tryJoinRawLines(@NotNull Document doc, @NotNull PsiFile file, int start, int endWithSpaces) {
    CharSequence text = doc.getCharsSequence();

    int end = getNextLineStart(start, text);
    start--;
    PsiJavaToken token = ObjectUtils.tryCast(file.findElementAt(start), PsiJavaToken.class);
    if (token == null) return CANNOT_JOIN;
    IElementType tokenType = token.getTokenType();
    if (!tokenType.equals(JavaTokenType.TEXT_BLOCK_LITERAL) &&
        !tokenType.equals(JavaTokenType.TEXT_BLOCK_TEMPLATE_BEGIN) &&
        !tokenType.equals(JavaTokenType.TEXT_BLOCK_TEMPLATE_END) &&
        !tokenType.equals(JavaTokenType.TEXT_BLOCK_TEMPLATE_MID)) {
      return CANNOT_JOIN;
    }
    boolean singleSlash = false;
    if (text.charAt(start) == '\\') {
      int lineNumber = doc.getLineNumber(start);
      int startOffset = Math.max(token.getTextRange().getStartOffset(), doc.getLineStartOffset(lineNumber));
      String substring = doc.getText(TextRange.create(startOffset, start)) + "\\\n";
      CharSequence parsed = CodeInsightUtilCore.parseStringCharacters(substring, null);
      singleSlash = parsed != null && parsed.charAt(parsed.length() - 1) != '\n';
    }
    if (!singleSlash) {
      start++;
    }
    int indent = token instanceof PsiFragment fragment
                 ? PsiFragmentImpl.getTextBlockFragmentIndent(fragment)
                 : BasicLiteralUtil.getTextBlockIndent(token);
    while (indent > 0 && text.charAt(end) == ' ' || text.charAt(end) == '\t') {
      indent--;
      end++;
    }
    if (singleSlash) {
      doc.deleteString(start, end);
    } else {
      doc.replaceString(start, end, "\\n");
    }
    return start;
  }

  private static int getNextLineStart(int start, CharSequence text) {
    int end = start;
    while (text.charAt(end) != '\n') {
      end++;
    }
    end++;
    return end;
  }

  @Override
  public int tryJoinLines(@NotNull Document document, @NotNull PsiFile file, int start, int end) {
    return CANNOT_JOIN;
  }
}
