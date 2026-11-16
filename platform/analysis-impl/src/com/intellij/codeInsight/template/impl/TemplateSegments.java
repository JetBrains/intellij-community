// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.template.impl;

import com.intellij.injected.editor.DocumentWindow;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@ApiStatus.Internal
public class TemplateSegments {
  private static final Key<Integer> SEGMENT_INDEX = Key.create("TemplateSegments.index");
  private final ArrayList<RangeMarker> mySegments = new ArrayList<>();
  private final @NotNull Document myDocument;

  TemplateSegments(@NotNull Document document) {
    myDocument = document;
  }

  public int getSegmentStart(int i) {
    RangeMarker rangeMarker = mySegments.get(i);
    return rangeMarker.getStartOffset();
  }

  public int getSegmentEnd(int i) {
    RangeMarker rangeMarker = mySegments.get(i);
    return rangeMarker.getEndOffset();
  }

  boolean isValid(int i) {
    return mySegments.get(i).isValid();
  }

  void removeAll() {
    for (RangeMarker segment : mySegments) {
      segment.dispose();
    }
    mySegments.clear();
  }

  void addSegment(int start, int end) {
    mySegments.add(createSegment(mySegments.size(), start, end));
  }

  private RangeMarker createSegment(int index, int start, int end) {
    RangeMarker rangeMarker = myDocument.createRangeMarker(start, end);
    rangeMarker.putUserData(SEGMENT_INDEX, index);
    return rangeMarker;
  }

  void setSegmentsGreedy(boolean greedy) {
    for (final RangeMarker segment : mySegments) {
      segment.setGreedyToRight(greedy);
      segment.setGreedyToLeft(greedy);
    }
  }

  boolean isInvalid() {
    for (RangeMarker marker : mySegments) {
      if (!marker.isValid()) {
        return true;
      }
    }
    return false;
  }

  void replaceSegmentAt(int index, int start, int end) {
    replaceSegmentAt(index, start, end, false);
  }

  void replaceSegmentAt(int index, int start, int end, boolean preserveGreediness) {
    RangeMarker rangeMarker = mySegments.get(index);
    boolean greedyToLeft = rangeMarker.isGreedyToLeft();
    boolean greedyToRight = rangeMarker.isGreedyToRight();
    if (!rangeMarker.isValid() || rangeMarker.getStartOffset() != start || rangeMarker.getEndOffset() != end) {
      rangeMarker.dispose();
      rangeMarker = createSegment(index, start, end);
    }
    rangeMarker.setGreedyToLeft(greedyToLeft || !preserveGreediness);
    rangeMarker.setGreedyToRight(greedyToRight || !preserveGreediness);
    mySegments.set(index, rangeMarker);
  }

  void fixOverlappedSegments(int currentSegment) {
    fixOverlappedSegments(currentSegment, false);
  }

  void fixOverlappedSegments(int currentSegment, boolean mayHaveInvalidSegments) {
    if (currentSegment < 0) return;
    int currentSegmentStart = getSegmentStart(currentSegment);
    int currentSegmentEnd = getSegmentEnd(currentSegment);
    if (currentSegmentStart == currentSegmentEnd) return;

    if (!mayHaveInvalidSegments && myDocument instanceof DocumentEx document && !(document instanceof DocumentWindow)) {
      List<Integer> overlappingSegments = new ArrayList<>();
      boolean completed = document.processRangeMarkersOverlappingWith(currentSegmentStart, currentSegmentEnd, marker -> {
        Integer index = marker.getUserData(SEGMENT_INDEX);
        if (index != null && index != currentSegment && index < mySegments.size() && mySegments.get(index) == marker) {
          overlappingSegments.add(index);
        }
        return true;
      });
      if (completed) {
        overlappingSegments.sort(null);
        for (int index : overlappingSegments) {
          fixOverlappedSegment(index, currentSegment, currentSegmentStart, currentSegmentEnd);
        }
        return;
      }
    }
    for (int i = 0; i < mySegments.size(); i++) {
      fixOverlappedSegment(i, currentSegment, currentSegmentStart, currentSegmentEnd);
    }
  }

  private void fixOverlappedSegment(int index, int currentSegment, int currentSegmentStart, int currentSegmentEnd) {
    if (index > currentSegment) {
      int startOffset = getSegmentStart(index);
      if (currentSegmentStart <= startOffset && startOffset < currentSegmentEnd) {
        replaceSegmentAt(index, currentSegmentEnd, Math.max(getSegmentEnd(index), currentSegmentEnd), true);
      }
    }
    else if (index < currentSegment) {
      int endOffset = getSegmentEnd(index);
      if (currentSegmentStart < endOffset && endOffset <= currentSegmentEnd) {
        replaceSegmentAt(index, Math.min(getSegmentStart(index), currentSegmentStart), currentSegmentStart, true);
      }
    }
  }

  void setNeighboursGreedy(final int segmentNumber, final boolean greedy) {
    if (segmentNumber > 0) {
      final RangeMarker left = mySegments.get(segmentNumber - 1);
      left.setGreedyToLeft(greedy);
      left.setGreedyToRight(greedy);
    }
    if (segmentNumber + 1 < mySegments.size()) {
      final RangeMarker right = mySegments.get(segmentNumber + 1);
      right.setGreedyToLeft(greedy);
      right.setGreedyToRight(greedy);
    }
  }

  /**
   * IDEADEV-13618
   *
   * prevent two different segments to grow simultaneously if they both starts at the same offset.
   */
  void lockSegmentAtTheSameOffsetIfAny(final int number) {
    if (number == -1) {
      return;
    }

    final RangeMarker current = mySegments.get(number);
    int offset = current.getStartOffset();

    for (int i = 0; i < mySegments.size(); i++) {
      if (i != number) {
        final RangeMarker segment = mySegments.get(i);
        final int startOffset2 = segment.getStartOffset();
        if (offset == startOffset2) {
          segment.setGreedyToLeft(false);
        }
      }
    }
  }

  int getSegmentsCount() {
    return mySegments.size();
  }
}