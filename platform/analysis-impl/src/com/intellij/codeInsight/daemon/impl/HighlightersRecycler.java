// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

// cache for highlighters not needed anymore.
// You call recycleHighlighter() to put unused highlighter into the cache
// and then call pickupHighlighterFromGarbageBin() (if there is a sudden need for fresh highlighter with specified offsets) to remove it from the cache to re-initialize and use.
final class HighlightersRecycler {
  private final MultiMap<TextRange, RangeHighlighterEx> incinerator = new MultiMap<>();
  private static final Key<Boolean> BEING_RECYCLED_KEY = Key.create("RECYCLED_KEY"); // set when the highlighter is just recycled, but not yet transferred to EDT to change its attributes. used to prevent double recycling the same RH

  // return true if RH is successfully recycled, false if race condition intervened
  boolean recycleHighlighter(@NotNull RangeHighlighterEx highlighter) {
    if (highlighter.isValid() && ((UserDataHolderEx)highlighter).replace(BEING_RECYCLED_KEY, null, Boolean.TRUE)) {
      incinerator.putValue(ProperTextRange.create(highlighter), highlighter);
      return true;
    }
    return false;
  }

  @Nullable // null means no highlighter found in the cache
  RangeHighlighterEx pickupHighlighterFromGarbageBin(int startOffset, int endOffset, int layer) {
    TextRange range = new TextRange(startOffset, endOffset);
    Collection<RangeHighlighterEx> collection = incinerator.get(range);
    for (RangeHighlighterEx highlighter : collection) {
      if (highlighter.isValid() && highlighter.getLayer() == layer) {
        incinerator.remove(range, highlighter);
        highlighter.putUserData(BEING_RECYCLED_KEY, null);
        return highlighter;
      }
    }
    return null;
  }

  @NotNull
  Collection<? extends RangeHighlighter> forAllInGarbageBin() {
    return incinerator.values();
  }

  // mark all remaining highlighters as not "recycled", to avoid double creation
  void releaseHighlighters() {
    for (RangeHighlighter highlighter : forAllInGarbageBin()) {
      ((UserDataHolderEx)highlighter).replace(BEING_RECYCLED_KEY, Boolean.TRUE, null);
    }
  }
  static boolean isBeingRecycled(RangeHighlighter highlighter) {
    return highlighter.getUserData(BEING_RECYCLED_KEY) != null;
  }
}
