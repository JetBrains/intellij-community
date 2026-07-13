// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.DocumentSnapshot;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.editor.impl.DocumentSnapshotImpl;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class DocumentInternalUtil {

  /**
   * Converts a logical column to a text offset inside the given text range, starting from a known column at the range start.
   * Tabs advance to the next tab stop, and surrogate pairs are treated as one logical column.
   */
  public static int calcLogicalOffset(
    @NotNull CharSequence text,
    int column,
    int startColumn,
    int startOffset,
    int endOffset,
    int tabSize
  ) {
    int currentColumn = startColumn;
    for (int i = startOffset; i < endOffset; i++) {
      char c = text.charAt(i);
      if (c == '\t') {
        currentColumn = (currentColumn / tabSize + 1) * tabSize;
      } else if (i + 1 < text.length() &&
                 Character.isHighSurrogate(c) &&
                 Character.isLowSurrogate(text.charAt(i + 1))) {
        if (currentColumn == column) {
          return i;
        }
      } else {
        currentColumn++;
      }
      if (currentColumn > column) {
        return i;
      }
    }
    return endOffset;
  }

  /**
   * Converts a text offset to a logical column, starting from a known offset and column.
   * Tabs advance to the next tab stop, and surrogate pairs are treated as one logical column.
   */
  public static int calcLogicalColumn(
    @NotNull CharSequence text,
    int startOffset,
    int startColumn,
    int offset,
    int tabSize
  ) {
    int column = startColumn;
    for (int i = startOffset; i < offset; i++) {
      char c = text.charAt(i);
      if (c == '\t') {
        column = (column / tabSize + 1) * tabSize;
      } else if (i + 1 >= text.length() ||
                 !Character.isHighSurrogate(c) ||
                 !Character.isLowSurrogate(text.charAt(i + 1))) {
        column++;
      }
    }
    return column;
  }

  public static @NotNull DocumentSnapshot getDocumentSnapshot(@NotNull Document document) {
    if (document instanceof DocumentImpl) {
      return ((DocumentImpl)document).getCore().snapshot();
    }
    return new DocumentSnapshotImpl(document.getImmutableCharSequence());
  }

  public static boolean isInsideSurrogatePair(@NotNull DocumentSnapshot snapshot, int offset) {
    return isSurrogatePair(snapshot, offset - 1);
  }

  public static boolean isSurrogatePair(@NotNull DocumentSnapshot snapshot, int offset) {
    CharSequence text = snapshot.charSequence();
    return offset >= 0 &&
           offset + 1 < text.length() &&
           Character.isHighSurrogate(text.charAt(offset)) &&
           Character.isLowSurrogate(text.charAt(offset + 1));
  }

  private DocumentInternalUtil() {
  }
}
