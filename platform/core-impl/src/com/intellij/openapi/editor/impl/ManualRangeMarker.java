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
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ProperTextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A range marker that has to be manually updated with {@link #applyEvents(List)}. Can hold PSI-based range and be updated when the document is committed.
 */
public class ManualRangeMarker {
  private ProperTextRange myRange;
  private boolean myValid = true;
  private final boolean myGreedyLeft;
  private final boolean myGreedyRight;
  private PersistentRangeMarker.LinesCols myLinesCols;

  public ManualRangeMarker(@NotNull FrozenDocument document, @NotNull ProperTextRange range, boolean greedyLeft, boolean greedyRight, boolean surviveOnExternalChange) {
    myRange = range;
    myGreedyLeft = greedyLeft;
    myGreedyRight = greedyRight;
    myLinesCols = surviveOnExternalChange ? PersistentRangeMarker.storeLinesAndCols(range, document) : null;
  }

  @Nullable
  public ProperTextRange getUpdatedRange(@NotNull List<DocumentEvent> events) {
    Pair<ProperTextRange, PersistentRangeMarker.LinesCols> pair = getUpdatedState(events);
    return pair == null ? null : pair.first;
  }

  @Nullable
  private Pair<ProperTextRange, PersistentRangeMarker.LinesCols> getUpdatedState(@NotNull List<DocumentEvent> events) {
    ProperTextRange range = myRange;
    PersistentRangeMarker.LinesCols linesCols = myLinesCols;
    for (DocumentEvent event : events) {
      if (linesCols != null) {
        Pair<ProperTextRange, PersistentRangeMarker.LinesCols> pair = PersistentRangeMarker
          .applyChange(event, range, range.getStartOffset(), range.getEndOffset(), myGreedyLeft, myGreedyRight, linesCols);
        if (pair == null) return null;

        range = pair.first;
        linesCols = pair.second;
      } else {
        range = RangeMarkerImpl.applyChange(event, range.getStartOffset(), range.getEndOffset(), myGreedyLeft, myGreedyRight);
        if (range == null) return null;
      }
    }
    return Pair.create(range, linesCols);
  }

  public boolean applyEvents(List<DocumentEvent> events) {
    if (!myValid) return false;

    Pair<ProperTextRange, PersistentRangeMarker.LinesCols> pair = getUpdatedState(events);
    if (pair != null) {
      myRange = pair.first;
      myLinesCols = pair.second;
    } else {
      myValid = false;
    }
    return myValid;
  }

  @Nullable
  public ProperTextRange getRange() {
    return myValid ? myRange : null;
  }
}
