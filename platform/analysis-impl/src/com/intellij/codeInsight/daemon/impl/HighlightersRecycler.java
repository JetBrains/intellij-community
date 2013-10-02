/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.util.SmartList;
import com.intellij.util.containers.MultiMap;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;

class HighlightersRecycler {
  private final MultiMap<TextRange, RangeHighlighter> incinerator = new MultiMap<TextRange, RangeHighlighter>(){
    @Override
    protected Map<TextRange, Collection<RangeHighlighter>> createMap() {
      return new THashMap<TextRange, Collection<RangeHighlighter>>();
    }

    @Override
    protected Collection<RangeHighlighter> createCollection() {
      return new SmartList<RangeHighlighter>();
    }
  };

  void recycleHighlighter(@NotNull RangeHighlighter highlighter) {
    if (highlighter.isValid()) {
      incinerator.putValue(ProperTextRange.create(highlighter), highlighter);
    }
  }

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
