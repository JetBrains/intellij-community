// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.template.impl;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

@ApiStatus.Internal
public class TemplateSegments {
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
    RangeMarker rangeMarker = myDocument.createRangeMarker(start, end);
    mySegments.add(rangeMarker);
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
    rangeMarker.dispose();
    
    rangeMarker = myDocument.createRangeMarker(start, end);
    rangeMarker.setGreedyToLeft(greedyToLeft || !preserveGreediness);
    rangeMarker.setGreedyToRight(greedyToRight || !preserveGreediness);
    mySegments.set(index, rangeMarker);
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