/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

/**
 * Is intended to hold utility methods to use during {@link Document} processing.
 */
public final class DocumentUtil {
  private DocumentUtil() {
  }

  /**
   * Ensures that given task is executed when given document is at the given 'in bulk' mode.
   * 
   * @param document       target document
   * @param executeInBulk  {@code true} to force given document to be in bulk mode when given task is executed;
   *                       {@code false} to force given document to be <b>not</b> in bulk mode when given task is executed
   * @param task           task to execute
   */
  public static void executeInBulk(@NotNull Document document, final boolean executeInBulk, @NotNull Runnable task) {
    if (executeInBulk == document.isInBulkUpdate()) {
      task.run();
      return;
    }

    //noinspection deprecation
    document.setInBulkUpdate(executeInBulk);
    try {
      task.run();
    }
    finally {
      //noinspection deprecation
      document.setInBulkUpdate(!executeInBulk);
    }
  }

  public static void executeInBulk(@NotNull Document document, @NotNull Runnable task) {
    executeInBulk(document, true, task);
  }

  public static void writeInRunUndoTransparentAction(@NotNull final Runnable runnable) {
    CommandProcessor.getInstance().runUndoTransparentAction(() -> ApplicationManager.getApplication().runWriteAction(runnable));
  }

  public static int getFirstNonSpaceCharOffset(@NotNull Document document, int line) {
    int startOffset = document.getLineStartOffset(line);
    int endOffset = document.getLineEndOffset(line);
    return getFirstNonSpaceCharOffset(document, startOffset, endOffset);
  }

  public static int getFirstNonSpaceCharOffset(@NotNull Document document, int startOffset, int endOffset) {
    CharSequence text = document.getImmutableCharSequence();
    for (int i = startOffset; i < endOffset; i++) {
      char c = text.charAt(i);
      if (c != ' ' && c != '\t') {
        return i;
      }
    }
    return startOffset;
  }

  public static boolean isValidOffset(int offset, @NotNull Document document) {
    return offset >= 0 && offset <= document.getTextLength();
  }

  public static int getLineStartOffset(int offset, @NotNull Document document) {
    if (offset < 0 || offset > document.getTextLength()) {
      return offset;
    }
    int lineNumber = document.getLineNumber(offset);
    return document.getLineStartOffset(lineNumber);
  }

  public static int getLineEndOffset(int offset, @NotNull Document document) {
    if (offset < 0 || offset > document.getTextLength()) {
      return offset;
    }
    int lineNumber = document.getLineNumber(offset);
    return document.getLineEndOffset(lineNumber);
  }

  @NotNull
  public static TextRange getLineTextRange(@NotNull Document document, int line) {
    return TextRange.create(document.getLineStartOffset(line), document.getLineEndOffset(line));
  }

  public static boolean isAtLineStart(int offset, @NotNull Document document) {
    return offset >= 0 && offset <= document.getTextLength() && offset == document.getLineStartOffset(document.getLineNumber(offset));
  }

  public static boolean isAtLineEnd(int offset, @NotNull Document document) {
    return offset >= 0 && offset <= document.getTextLength() && offset == document.getLineEndOffset(document.getLineNumber(offset));
  }

  public static int alignToCodePointBoundary(@NotNull Document document, int offset) {
    return isInsideSurrogatePair(document, offset) ? offset - 1 : offset;
  }

  public static boolean isSurrogatePair(@NotNull Document document, int offset) {
    CharSequence text = document.getImmutableCharSequence();
    return offset >= 0 &&
           offset + 1 < text.length() &&
           Character.isHighSurrogate(text.charAt(offset)) &&
           Character.isLowSurrogate(text.charAt(offset + 1));
  }

  public static boolean isInsideSurrogatePair(@NotNull Document document, int offset) {
    return isSurrogatePair(document, offset - 1);
  }

  public static int getPreviousCodePointOffset(@NotNull Document document, int offset) {
    return offset - (isSurrogatePair(document, offset - 2) ? 2 : 1);
  }

  public static int getNextCodePointOffset(@NotNull Document document, int offset) {
    return offset + (isSurrogatePair(document, offset) ? 2 : 1);
  }

  /**
   * Tells whether given offset lies between surrogate pair characters or between characters of Windows-style line break (\r\n).
   */
  public static boolean isInsideCharacterPair(@NotNull Document document, int offset) {
    if (offset <= 0 || offset >= document.getTextLength()) return false;
    CharSequence text = document.getImmutableCharSequence();
    char prev = text.charAt(offset - 1);
    return prev == '\r' ? text.charAt(offset) == '\n' : Character.isHighSurrogate(prev) && Character.isLowSurrogate(text.charAt(offset));
  }

  public static boolean isLineEmpty(@NotNull Document document, final int line) {
    final CharSequence chars = document.getCharsSequence();
    int start = document.getLineStartOffset(line);
    int end = Math.min(document.getLineEndOffset(line), document.getTextLength() - 1);
    for (int i = start; i <= end; i++) {
      if (!Character.isWhitespace(chars.charAt(i))) return false;
    }
    return true;
  }

  /**
   * Calculates indent of the line containing {@code offset}
   * @return Whitespaces at the beginning of the line
   */
  public static CharSequence getIndent(@NotNull Document document, int offset) {
    int lineOffset = getLineStartOffset(offset, document);
    int result = 0;
    while (lineOffset + result < document.getTextLength() &&
           Character.isWhitespace(document.getCharsSequence().charAt(lineOffset + result))) {
      result++;
    }
    if (result + lineOffset > document.getTextLength()) {
      result--;
    }
    return document.getCharsSequence().subSequence(lineOffset, lineOffset + Math.max(result, 0));
  }

  public static int calculateOffset(@NotNull Document document, int line, int column, int tabSize) {
    int offset;
    if (0 <= line && line < document.getLineCount()) {
      final int lineStart = document.getLineStartOffset(line);
      final int lineEnd = document.getLineEndOffset(line);
      final CharSequence docText = document.getCharsSequence();

      offset = lineStart;
      int col = 0;
      while (offset < lineEnd && col < column) {
        col += docText.charAt(offset) == '\t' ? tabSize : 1;
        offset++;
      }
    }
    else {
      offset = document.getTextLength();
    }
    return offset;
  }
}
