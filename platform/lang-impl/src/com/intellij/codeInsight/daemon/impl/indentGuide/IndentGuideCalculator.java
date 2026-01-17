// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.indentGuide;

import com.intellij.codeInsight.highlighting.BraceMatchingUtil;
import com.intellij.codeInsight.highlighting.CodeBlockSupportHandler;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;


final class IndentGuideCalculator {
  private final Editor editor;
  private final Document document;
  private final PsiFile psiFile;
  private final Map<Language, TokenSet> comments = new HashMap<>();

  IndentGuideCalculator(@NotNull Editor editor, @NotNull PsiFile file) {
    this.editor = editor;
    this.document = editor.getDocument();
    this.psiFile = file;
  }

  int @NotNull [] calculate(int tabSize) {
    FileType fileType = psiFile.getFileType();
    CharSequence myChars = document.getCharsSequence();

    // negative value means the line is empty (or contains a comment) and indent
    // (denoted by absolute value) was deduced from enclosing non-empty lines
    int [] lineIndents = new int[document.getLineCount()];

    for (int line = 0; line < lineIndents.length; line++) {
      ProgressManager.checkCanceled();
      int lineStart = document.getLineStartOffset(line);
      int lineEnd = document.getLineEndOffset(line);
      int offset = lineStart;
      int column = 0;
      outer:
      while (offset < lineEnd) {
        switch (myChars.charAt(offset)) {
          case ' ':
            column++;
            break;
          case '\t':
            column = (column / tabSize + 1) * tabSize;
            break;
          default:
            break outer;
        }
        offset++;
      }
      // treating commented lines in the same way as empty lines
      // Blank line marker
      lineIndents[line] = offset == lineEnd || isComment(offset) ? -1 : column;
    }

    int topIndent = 0;
    for (int line = 0; line < lineIndents.length; line++) {
      ProgressManager.checkCanceled();
      if (lineIndents[line] >= 0) {
        topIndent = lineIndents[line];
      }
      else {
        int startLine = line;
        while (line < lineIndents.length && lineIndents[line] < 0) {
          //noinspection AssignmentToForLoopParameter
          line++;
        }

        int bottomIndent = line < lineIndents.length ? lineIndents[line] : topIndent;

        int indent = Math.min(topIndent, bottomIndent);
        if (bottomIndent < topIndent) {
          int lineStart = document.getLineStartOffset(line);
          int lineEnd = document.getLineEndOffset(line);
          int nonWhitespaceOffset = CharArrayUtil.shiftForward(myChars, lineStart, lineEnd, " \t");
          HighlighterIterator iterator = editor.getHighlighter().createIterator(nonWhitespaceOffset);
          IElementType tokenType = iterator.getTokenType();
          if (BraceMatchingUtil.isRBraceToken(iterator, myChars, fileType) ||
              tokenType != null &&
              !CodeBlockSupportHandler.findMarkersRanges(psiFile, tokenType.getLanguage(), nonWhitespaceOffset).isEmpty()) {
            indent = topIndent;
          }
        }

        for (int blankLine = startLine; blankLine < line; blankLine++) {
          assert lineIndents[blankLine] == -1;
          lineIndents[blankLine] = -Math.min(topIndent, indent);
        }

        //noinspection AssignmentToForLoopParameter
        line--; // will be incremented back at the end of the loop;
      }
    }
    return lineIndents;
  }

  private boolean isComment(int offset) {
    HighlighterIterator it = editor.getHighlighter().createIterator(offset);
    IElementType tokenType = it.getTokenType();
    Language language = tokenType.getLanguage();
    TokenSet comments = this.comments.get(language);
    if (comments == null) {
      ParserDefinition definition = LanguageParserDefinitions.INSTANCE.forLanguage(language);
      if (definition != null) {
        comments = definition.getCommentTokens();
      }
      if (comments == null) {
        return false;
      }
      else {
        this.comments.put(language, comments);
      }
    }
    return comments.contains(tokenType);
  }
}
