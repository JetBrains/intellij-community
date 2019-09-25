// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

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

public class StringContentIndentUtil {

  private static final Key<Integer> TEXT_BLOCK_INDENT_KEY = Key.create("TextBlockOffset");
  private static final Key<List<RangeHighlighter>> TEXT_BLOCK_HIGHLIGHTERS_IN_EDITOR_KEY = Key.create("TextBlockHighlightersInEditor");

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
