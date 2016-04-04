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
import com.intellij.openapi.editor.impl.event.DocumentEventImpl;
import com.intellij.openapi.editor.impl.event.RetargetRangeMarkers;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A range marker that has to be manually updated with {@link #getUpdatedRange(DocumentEvent, FrozenDocument)}.
 * Can hold PSI-based range and be updated when the document is committed.
 */
public class ManualRangeMarker implements Segment {
  private final int myStart;
  private final int myEnd;
  private final boolean myGreedyLeft;
  private final boolean myGreedyRight;
  private final boolean mySurviveOnExternalChange;
  private final PersistentRangeMarker.LinesCols myLinesCols;

  public ManualRangeMarker(int start, int end,
                            boolean greedyLeft,
                            boolean greedyRight,
                            boolean surviveOnExternalChange,
                            @Nullable PersistentRangeMarker.LinesCols linesCols) {
    myStart = start;
    myEnd = end;
    myGreedyLeft = greedyLeft;
    myGreedyRight = greedyRight;
    mySurviveOnExternalChange = surviveOnExternalChange;
    myLinesCols = linesCols;
  }

  @Nullable
  public ManualRangeMarker getUpdatedRange(@NotNull DocumentEvent event, @NotNull FrozenDocument documentBefore) {
    if (event instanceof RetargetRangeMarkers) {
      int start = ((RetargetRangeMarkers)event).getStartOffset();
      if (myStart >= start && myEnd <= ((RetargetRangeMarkers)event).getEndOffset()) {
        int delta = ((RetargetRangeMarkers)event).getMoveDestinationOffset() - start;
        return new ManualRangeMarker(myStart + delta, myEnd + delta, myGreedyLeft, myGreedyRight, mySurviveOnExternalChange, null);
      }
    }

    if (mySurviveOnExternalChange && PersistentRangeMarkerUtil.shouldTranslateViaDiff(event, myStart, myEnd)) {
      PersistentRangeMarker.LinesCols linesCols = myLinesCols != null ? myLinesCols
                                                                      : PersistentRangeMarker.storeLinesAndCols(documentBefore, myStart, myEnd);
      Pair<TextRange, PersistentRangeMarker.LinesCols> pair =
        linesCols == null ? null : PersistentRangeMarker.translateViaDiff((DocumentEventImpl)event, linesCols);
      if (pair != null) {
        return new ManualRangeMarker(pair.first.getStartOffset(), pair.first.getEndOffset(), myGreedyLeft, myGreedyRight, true, pair.second);
      }
    }

    TextRange range = RangeMarkerImpl.applyChange(event, myStart, myEnd, myGreedyLeft, myGreedyRight);
    return range == null ? null : new ManualRangeMarker(range.getStartOffset(), range.getEndOffset(), myGreedyLeft, myGreedyRight, mySurviveOnExternalChange, null);
  }

  @Override
  public int getStartOffset() {
    return myStart;
  }

  @Override
  public int getEndOffset() {
    return myEnd;
  }

  @Override
  public String toString() {
    return "ManualRangeMarker" + TextRange.create(this);
  }

}
