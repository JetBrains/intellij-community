// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

// cache for highlighters not needed anymore.
// You call recycleHighlighter() to put unused highlighter into the cache
// and then call pickupHighlighterFromGarbageBin() (if there is a sudden need for fresh highlighter with specified offsets) to remove it from the cache to re-initialize and use.
final class HighlightersRecycler {
  private final MultiMap<TextRange, RangeHighlighter> incinerator = new MultiMap<>();

  void recycleHighlighter(@NotNull RangeHighlighter highlighter) {
    if (highlighter.isValid()) {
      incinerator.putValue(ProperTextRange.create(highlighter), highlighter);
    }
  }

  @Nullable // null means no highlighter found in the cache
  RangeHighlighter pickupHighlighterFromGarbageBin(int startOffset, int endOffset, int layer) {
    TextRange range = new TextRange(startOffset, endOffset);
    Collection<RangeHighlighter> collection = incinerator.get(range);
    for (RangeHighlighter highlighter : collection) {
      if (highlighter.isValid() && highlighter.getLayer() == layer) {
        incinerator.remove(range, highlighter);
        return highlighter;
      }
    }
    return null;
  }

  @NotNull
  Collection<? extends RangeHighlighter> forAllInGarbageBin() {
    return incinerator.values();
  }
}
