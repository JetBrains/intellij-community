/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.codeInsight.template.impl;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.RangeMarkerEx;

import java.util.ArrayList;

public class TemplateSegments {
  private final ArrayList<RangeMarker> mySegments = new ArrayList<RangeMarker>();
  private final Editor myEditor;

  public TemplateSegments(Editor editor) {
    myEditor = editor;
  }

  public int getSegmentStart(int i) {
    RangeMarker rangeMarker = mySegments.get(i);
    return rangeMarker.getStartOffset();
  }

  public int getSegmentEnd(int i) {
    RangeMarker rangeMarker = mySegments.get(i);
    return rangeMarker.getEndOffset();
  }

  public boolean isValid(int i) {
    return mySegments.get(i).isValid();
  }

  public void removeAll() {
    mySegments.clear();
  }

  public void addSegment(int start, int end) {
    RangeMarker rangeMarker = myEditor.getDocument().createRangeMarker(start, end);
    mySegments.add(rangeMarker);
  }

  public void setSegmentsGreedy(boolean greedy) {
    for (final RangeMarker segment : mySegments) {
      segment.setGreedyToRight(greedy);
      segment.setGreedyToLeft(greedy);
    }
  }

  public boolean isInvalid() {
    for (RangeMarker marker : mySegments) {
      if (!marker.isValid()) {
        return true;
      }
    }
    return false;
  }

  public void replaceSegmentAt(int index, int start, int end) {
    RangeMarker rangeMarker = mySegments.get(index);
    ((DocumentEx)rangeMarker.getDocument()).removeRangeMarker((RangeMarkerEx)rangeMarker);
    Document doc = myEditor.getDocument();
    rangeMarker = doc.createRangeMarker(start, end);
    rangeMarker.setGreedyToLeft(true);
    rangeMarker.setGreedyToRight(true);
    mySegments.set(index, rangeMarker);
  }

  public void setNeighboursGreedy(final int segmentNumber, final boolean greedy) {
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
  public void lockSegmentAtTheSameOffsetIfAny(final int number) {
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

  public int getSegmentWithTheSameStart(int segmentNumber, int start) {
    for (int i = segmentNumber + 1; i < mySegments.size(); i++) {
      final RangeMarker segment = mySegments.get(i);
      final int startOffset2 = segment.getStartOffset();
      if (start == startOffset2) {
        return i;
      }
    }

    return -1;
  }
}