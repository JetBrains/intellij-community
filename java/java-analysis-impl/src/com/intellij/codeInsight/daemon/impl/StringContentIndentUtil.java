// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class StringContentIndentUtil {

  private static final Key<Integer> TEXT_BLOCK_INDENT_KEY = Key.create("TextBlockOffset");
  private static final Key<List<RangeHighlighter>> TEXT_BLOCK_HIGHLIGHTERS_IN_EDITOR_KEY = Key.create("TextBlockHighlightersInEditor");
  private static final Key<Long> LAST_TIME_CONTENT_INDENT_CHANGED = Key.create("LastTimeContentIndentChanged");

  static boolean isDocumentUpdated(@NotNull Editor editor) {
    Document document = editor.getDocument();
    long stamp = getTimestamp(editor, document);
    Long prevStamp = document.getUserData(LAST_TIME_CONTENT_INDENT_CHANGED);
    return prevStamp == null || prevStamp != stamp;
  }

  static void updateTimestamp(@NotNull Editor editor) {
    Document document = editor.getDocument();
    long timestamp = getTimestamp(editor, document);
    document.putUserData(LAST_TIME_CONTENT_INDENT_CHANGED, timestamp);
  }

  private static long getTimestamp(@NotNull Editor editor, Document document) {
    return editor.getSettings().isIndentGuidesShown() ? document.getModificationStamp() : -1;
  }

  /**
   * Extract indent highlighters from editor and group them by (startOffset, endOffset) ranges.
   */
  @NotNull
  public static Map<TextRange, RangeHighlighter> getIndentHighlighters(@NotNull Editor editor) {
    List<RangeHighlighter> highlighters = editor.getUserData(TEXT_BLOCK_HIGHLIGHTERS_IN_EDITOR_KEY);
    if (highlighters == null) return Collections.emptyMap();
    return highlighters.stream().collect(Collectors.toMap(h -> new TextRange(h.getStartOffset(), h.getEndOffset()), Function.identity()));
  }

  /**
   * Replace indent highlighters in editor with a new ones.
   */
  public static void addIndentHighlighters(@NotNull Editor editor, @NotNull List<RangeHighlighter> highlighters) {
    editor.putUserData(TEXT_BLOCK_HIGHLIGHTERS_IN_EDITOR_KEY, highlighters);
  }

  /**
   * Extract string content indent from highlighter.
   *
   * @return indent or -1 if highlighter doesn't contain indent data.
   */
  public static int getIndent(@NotNull RangeHighlighter highlighter) {
    Integer indent = highlighter.getUserData(TEXT_BLOCK_INDENT_KEY);
    return indent == null ? -1 : indent;
  }

  /**
   * Store indent data in highlighter.
   */
  public static void setIndent(@NotNull RangeHighlighter highlighter, int indent) {
    highlighter.putUserData(TEXT_BLOCK_INDENT_KEY, indent);
  }
}
