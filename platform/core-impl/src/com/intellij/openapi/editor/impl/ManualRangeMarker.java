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
import com.intellij.openapi.editor.impl.event.RetargetRangeMarkers;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ProperTextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A range marker that has to be manually updated with {@link #getUpdatedRange(DocumentEvent)}.
 * Can hold PSI-based range and be updated when the document is committed.
 */
public class ManualRangeMarker {
  private final ProperTextRange myRange;
  private final boolean myGreedyLeft;
  private final boolean myGreedyRight;
  private final PersistentRangeMarker.LinesCols myLinesCols;

  public ManualRangeMarker(@NotNull FrozenDocument document, @NotNull ProperTextRange range, boolean greedyLeft, boolean greedyRight, boolean surviveOnExternalChange) {
    this(range, greedyLeft, greedyRight, surviveOnExternalChange ? PersistentRangeMarker.storeLinesAndCols(range, document) : null);
  }

  private ManualRangeMarker(@NotNull ProperTextRange range,
                            boolean greedyLeft,
                            boolean greedyRight,
                            @Nullable PersistentRangeMarker.LinesCols linesCols) {
    myRange = range;
    myGreedyLeft = greedyLeft;
    myGreedyRight = greedyRight;
    myLinesCols = linesCols;
  }

  @Nullable
  public ManualRangeMarker getUpdatedRange(@NotNull DocumentEvent event) {
    if (event instanceof RetargetRangeMarkers) {
      int start = ((RetargetRangeMarkers)event).getStartOffset();
      if (myRange.getStartOffset() >= start && myRange.getEndOffset() <= ((RetargetRangeMarkers)event).getEndOffset()) {
        ProperTextRange range = myRange.shiftRight(((RetargetRangeMarkers)event).getMoveDestinationOffset() - start);
        return new ManualRangeMarker(range, myGreedyLeft, myGreedyRight, myLinesCols == null ? null : PersistentRangeMarker.storeLinesAndCols(range, event.getDocument()));
      }
    }

    if (myLinesCols != null) {
      Pair<ProperTextRange, PersistentRangeMarker.LinesCols> pair = PersistentRangeMarker
        .applyChange(event, myRange, myRange.getStartOffset(), myRange.getEndOffset(), myGreedyLeft, myGreedyRight, myLinesCols);
      return pair == null ? null : new ManualRangeMarker(pair.first, myGreedyLeft, myGreedyRight, pair.second);
    }
    
    ProperTextRange range = RangeMarkerImpl.applyChange(event, myRange.getStartOffset(), myRange.getEndOffset(), myGreedyLeft, myGreedyRight);
    return range == null ? null : new ManualRangeMarker(range, myGreedyLeft, myGreedyRight, null);
  }

  @NotNull
  public ProperTextRange getRange() {
    return myRange;
  }

  @Override
  public String toString() {
    return "ManualRangeMarker" + myRange;
  }

}
