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
class HighlightersRecycler {
  private final MultiMap<TextRange, RangeHighlighter> incinerator = MultiMap.createSmart();

  void recycleHighlighter(@NotNull RangeHighlighter highlighter) {
    if (highlighter.isValid()) {
      incinerator.putValue(ProperTextRange.create(highlighter), highlighter);
    }
  }

  @Nullable // null means no highlighter found in the cache
  RangeHighlighter pickupHighlighterFromGarbageBin(int startOffset, int endOffset, int layer){
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
