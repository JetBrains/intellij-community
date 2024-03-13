// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.java.PsiFragmentImpl;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiLiteralUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

public final class TextBlockJoinLinesHandler implements JoinRawLinesHandlerDelegate {
  private static final Pattern TEXT_BLOCK_START = Pattern.compile("^\"\"\"[ \t\f]*\n", Pattern.MULTILINE);
  
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
    TextRange tokenRange = token.getTextRange();
    int lineNumber = doc.getLineNumber(start);
    boolean atStartLine = (tokenType.equals(JavaTokenType.TEXT_BLOCK_LITERAL) || tokenType.equals(JavaTokenType.TEXT_BLOCK_TEMPLATE_BEGIN))
                          && lineNumber == doc.getLineNumber(tokenRange.getStartOffset());
    boolean atEmptyStartLine = atStartLine && TEXT_BLOCK_START.matcher(token.getText()).find();
    boolean singleSlash = false;
    if (text.charAt(start) == '\\') {
      int startOffset = Math.max(tokenRange.getStartOffset(), doc.getLineStartOffset(lineNumber));
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
    boolean fromStartTillEnd = atStartLine && tokenType.equals(JavaTokenType.TEXT_BLOCK_LITERAL) && 
                               doc.getLineNumber(tokenRange.getEndOffset()) == lineNumber + 1 &&
                               token.getText().endsWith("\"\"\"");
    if (singleSlash || atEmptyStartLine) {
      doc.deleteString(start, end);
      end = start;
    } else {
      doc.replaceString(start, end, "\\n");
      end = start + 2;
    }
    if (fromStartTillEnd) {
      doc.replaceString(tokenRange.getStartOffset(), end + 3, 
                        convertToRegular(doc.getText().substring(tokenRange.getStartOffset(), end + 3)));
    }
    return start;
  }

  private static @NotNull String convertToRegular(@NotNull String literal) {
    if (literal.length() < 6 || !literal.startsWith("\"\"\"") || !literal.endsWith("\"\"\"")) {
      return literal;
    }
    int end = literal.length() - 3;
    StringBuilder sb = null;
    for (int i = 3; i < end; i++) {
      char ch = literal.charAt(i);
      if (ch == '"') {
        if (sb == null) {
          sb = new StringBuilder(literal.substring(3, i));
        }
        sb.append("\\\"");
      } else if (sb != null) {
        sb.append(ch);
      }
      int nextI = PsiLiteralUtil.parseBackSlash(literal, i);
      if (nextI != -1) {
        if (sb != null) {
          sb.append(literal, i + 1, nextI + 1);
        }
        //noinspection AssignmentToForLoopParameter
        i = nextI;
      }
    }
    return sb == null ? literal.substring(2, end + 1) : '"' + sb.toString() + '"';
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
