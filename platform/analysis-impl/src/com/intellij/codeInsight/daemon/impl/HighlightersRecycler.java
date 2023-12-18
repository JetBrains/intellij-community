// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.impl.RangeMarkerImpl;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRangeScalarUtil;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.util.containers.ContainerUtil;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Cache for highlighters scheduled for removal.
 * You call {@link #recycleHighlighter} to put unused highlighter into the cache
 * and then call {@link #pickupHighlighterFromGarbageBin} (if there is a sudden need for fresh highlighter with specified offsets) to remove it from the cache to re-initialize and re-use.
 * In the end, call {@link UpdateHighlightersUtil#incinerateObsoleteHighlighters} to finally remove highlighters left in the cache that nobody picked up and reused.
 */
final class HighlightersRecycler {
  private final Long2ObjectMap<List<RangeHighlighterEx>> incinerator = new Long2ObjectOpenHashMap<>();
  private static final Key<Boolean> BEING_RECYCLED_KEY = Key.create("RECYCLED_KEY"); // set when the highlighter is just recycled, but not yet transferred to EDT to change its attributes. used to prevent double recycling the same RH

  // return true if RH is successfully recycled, false if race condition intervened
  boolean recycleHighlighter(@NotNull RangeHighlighterEx highlighter) {
    if (highlighter.isValid() && ((UserDataHolderEx)highlighter).replace(BEING_RECYCLED_KEY, null, Boolean.TRUE)) {
      long range = ((RangeMarkerImpl)highlighter).getScalarRange();
      incinerator.computeIfAbsent(range, __ -> new ArrayList<>()).add(highlighter);
      return true;
    }
    return false;
  }

  @Nullable // null means no highlighter found in the cache
  RangeHighlighterEx pickupHighlighterFromGarbageBin(int startOffset, int endOffset, int layer) {
    long range = TextRangeScalarUtil.toScalarRange(startOffset, endOffset);
    List<RangeHighlighterEx> collection = incinerator.get(range);
    if (collection != null) {
      for (int i = 0; i < collection.size(); i++) {
        RangeHighlighterEx highlighter = collection.get(i);
        if (highlighter.isValid() && highlighter.getLayer() == layer) {
          collection.remove(i);
          if (collection.isEmpty()) {
            incinerator.remove(range);
          }
          highlighter.putUserData(BEING_RECYCLED_KEY, null);
          return highlighter;
        }
      }
    }
    return null;
  }

  @NotNull
  Collection<? extends RangeHighlighter> forAllInGarbageBin() {
    return ContainerUtil.flatten(incinerator.values());
  }

  // mark all remaining highlighters as not "recycled", to avoid double creation
  void releaseHighlighters() {
    for (RangeHighlighter highlighter : forAllInGarbageBin()) {
      ((UserDataHolderEx)highlighter).replace(BEING_RECYCLED_KEY, Boolean.TRUE, null);
    }
  }

  static boolean isBeingRecycled(@NotNull RangeHighlighter highlighter) {
    return highlighter.getUserData(BEING_RECYCLED_KEY) != null;
  }
}
