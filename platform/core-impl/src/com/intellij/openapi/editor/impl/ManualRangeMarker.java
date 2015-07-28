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

import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ProperTextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A range marker that has to be manually updated with {@link #applyEvent(DocumentEvent)}. Can hold PSI-based range and be updated when the document is committed.
 */
public class ManualRangeMarker {
  private ProperTextRange myRange;
  private boolean myValid = true;
  private final boolean myGreedyLeft;
  private final boolean myGreedyRight;
  private PersistentRangeMarker.LinesCols myLinesCols;

  public ManualRangeMarker(@NotNull FrozenDocument document, @NotNull ProperTextRange range, boolean greedyLeft, boolean greedyRight, boolean surviveOnExternalChange) {
    this(range, greedyLeft, greedyRight, surviveOnExternalChange ? PersistentRangeMarker.storeLinesAndCols(range, document) : null);
  }

  private ManualRangeMarker(ProperTextRange range,
                           boolean greedyLeft,
                           boolean greedyRight,
                           PersistentRangeMarker.LinesCols linesCols) {
    myRange = range;
    myGreedyLeft = greedyLeft;
    myGreedyRight = greedyRight;
    myLinesCols = linesCols;
  }

  @Nullable
  public ManualRangeMarker getUpdatedRange(@NotNull DocumentEvent event) {
    Pair<ProperTextRange, PersistentRangeMarker.LinesCols> pair = getUpdatedState(event);
    return pair == null ? null : new ManualRangeMarker(pair.first, myGreedyLeft, myGreedyRight, pair.second);
  }

  @Nullable
  private Pair<ProperTextRange, PersistentRangeMarker.LinesCols> getUpdatedState(@NotNull DocumentEvent event) {
    if (myLinesCols != null) {
      return PersistentRangeMarker
        .applyChange(event, myRange, myRange.getStartOffset(), myRange.getEndOffset(), myGreedyLeft, myGreedyRight, myLinesCols);
    }
    
    ProperTextRange range = RangeMarkerImpl.applyChange(event, myRange.getStartOffset(), myRange.getEndOffset(), myGreedyLeft, myGreedyRight);
    return range == null ? null : new Pair<ProperTextRange, PersistentRangeMarker.LinesCols>(range, null);
  }

  public void applyEvent(@NotNull DocumentEvent event) {
    if (!myValid) return;

    Pair<ProperTextRange, PersistentRangeMarker.LinesCols> pair = getUpdatedState(event);
    if (pair != null) {
      myRange = pair.first;
      myLinesCols = pair.second;
    } else {
      myValid = false;
    }
  }

  @Nullable
  public ProperTextRange getRange() {
    return myValid ? myRange : null;
  }

  public boolean isValid() {
    return myValid;
  }
}
