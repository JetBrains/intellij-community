// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.util;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * A line and a column of a document, both counted from zero. The column is a number of characters from the start of the line.
 * <p>
 * The diff code uses {@link LineCol} instead of {@link com.intellij.openapi.editor.LogicalPosition}.
 * A logical position is the position of the caret, that can be out of sync with the text offset: a file that uses `\t` as indent can put
 * caret in the middle of the tabulation, and thus gains imaginary columns. We need Editor-setting-agnostic representation
 * that is directly mapped to the Document offsets.
 */
public class LineCol {
  public final int line;
  public final int column;

  public LineCol(int line) {
    this(line, 0);
  }

  public LineCol(int line, int column) {
    this.line = line;
    this.column = column;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    LineCol col = (LineCol)o;

    if (line != col.line) return false;
    if (column != col.column) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = line;
    result = 31 * result + column;
    return result;
  }

  @Override
  public @NonNls String toString() {
    return String.format("{ line: %s, column: %s }", line, column);
  }

  /** The position of {@code offset}. An offset at or past the end of the text gives the end of the last line. */
  public static @NotNull LineCol fromOffset(@NotNull Document document, int offset) {
    if (offset < document.getTextLength()) {
      int line = document.getLineNumber(offset);
      int column = offset - document.getLineStartOffset(line);
      return new LineCol(line, column);
    }
    else {
      int line = Math.max(0, document.getLineCount() - 1);
      int column = document.getLineEndOffset(line) - document.getLineStartOffset(line);
      return new LineCol(line, column);
    }
  }

  /** The position of the primary caret of {@code editor} */
  public static @NotNull LineCol fromCaret(@NotNull Editor editor) {
    return fromOffset(editor.getDocument(), editor.getCaretModel().getOffset());
  }

  public static int toOffset(@NotNull Document document, @NotNull LineCol linecol) {
    return linecol.toOffset(document);
  }

  public static int toOffset(@NotNull Document document, int line, int col) {
    return new LineCol(line, col).toOffset(document);
  }

  public int toOffset(@NotNull Document document) {
    if (line >= document.getLineCount()) return document.getTextLength();
    return Math.min(document.getLineStartOffset(line) + column, document.getLineEndOffset(line));
  }

  public int toOffset(@NotNull Editor editor) {
    return toOffset(editor.getDocument());
  }
}
