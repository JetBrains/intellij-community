// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.impl.event.DocumentEventImpl;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.DocumentEventUtil;
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
    if (mySurviveOnExternalChange && PersistentRangeMarkerUtil.shouldTranslateViaDiff(event, myStart, myEnd)) {
      PersistentRangeMarker.LinesCols linesCols = myLinesCols != null ? myLinesCols
                                                                      : PersistentRangeMarker.storeLinesAndCols(documentBefore, myStart, myEnd);
      Pair<TextRange, PersistentRangeMarker.LinesCols> pair =
        linesCols == null ? null : PersistentRangeMarker.translateViaDiff((DocumentEventImpl)event, linesCols);
      if (pair != null) {
        return new ManualRangeMarker(pair.first.getStartOffset(), pair.first.getEndOffset(), myGreedyLeft, myGreedyRight, true, pair.second);
      }
    }

    TextRange range = RangeMarkerImpl.applyChange(event, myStart, myEnd, myGreedyLeft, myGreedyRight, false);
    if (range == null) return null;

    int delta = 0;
    if (DocumentEventUtil.isMoveInsertion(event)) {
      int srcOffset = event.getMoveOffset();
      if (srcOffset <= range.getStartOffset() && range.getEndOffset() <= srcOffset + event.getNewLength()) {
        delta = event.getOffset() - srcOffset;
      }
    }
    return new ManualRangeMarker(range.getStartOffset() + delta, range.getEndOffset() + delta, myGreedyLeft, myGreedyRight,
                                 mySurviveOnExternalChange, null);
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
