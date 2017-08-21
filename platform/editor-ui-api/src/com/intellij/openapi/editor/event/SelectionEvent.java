/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.editor.event;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;

import java.util.EventObject;

public class SelectionEvent extends EventObject {
  private static final TextRange[] EMPTY_RANGES = new TextRange[0];
  private Pair<TextRange[], TextRange> myOldRanges;
  private Pair<TextRange[], TextRange> myNewRanges;


  public SelectionEvent(Editor editor,
                        int[] oldSelectionStarts, int[] oldSelectionEnds,
                        int[] newSelectionStarts, int[] newSelectionEnds) {
    super(editor);

    assertCorrectSelection(oldSelectionStarts, oldSelectionEnds);
    assertCorrectSelection(newSelectionStarts, newSelectionEnds);

    myOldRanges = getRanges(oldSelectionStarts, oldSelectionEnds);
    myNewRanges = getRanges(newSelectionStarts, newSelectionEnds);

  }

  public SelectionEvent(Editor editor, int oldStart, int oldEnd, int newStart, int newEnd) {
    this(editor, new int[]{oldStart}, new int[]{oldEnd}, new int[]{newStart}, new int[]{newEnd});
  }

  private static void assertCorrectSelection(int[] starts, int[] ends) {
    assert starts.length == ends.length;
  }

  public Editor getEditor() {
    return (Editor) getSource();
  }

  public TextRange getOldRange() {
    return myOldRanges.second;
  }

  public TextRange getNewRange() {
    return myNewRanges.second;
  }

  public TextRange[] getOldRanges() {
    return myOldRanges.first;
  }

  public TextRange[] getNewRanges() {
    return myNewRanges.first;
  }

  private static Pair<TextRange[], TextRange> getRanges(int[] starts, int[] ends) {
    if (starts.length == 0) {
      return Pair.create(EMPTY_RANGES, TextRange.EMPTY_RANGE);
    }
    final TextRange[] ranges = new TextRange[starts.length];
    int startOffset = Integer.MAX_VALUE;
    int endOffset = Integer.MIN_VALUE;
    for (int i = 0; i < starts.length; ++i) {
      int start = starts[i];
      int end = ends[i];
      ranges[i] = new TextRange(start, end);
      startOffset = Math.min(startOffset, start);
      endOffset = Math.max(endOffset, end);
    }
    return Pair.create(ranges, new TextRange(startOffset, endOffset));
  }

}
