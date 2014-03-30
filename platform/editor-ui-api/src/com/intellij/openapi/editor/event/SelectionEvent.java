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
package com.intellij.openapi.editor.event;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;

import java.util.Arrays;
import java.util.EventObject;

public class SelectionEvent extends EventObject {
  private static final TextRange[] EMPTY_RANGES = new TextRange[0];
  private TextRange[] myOldRanges;
  private TextRange[] myNewRanges;


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
    return getRange(myOldRanges);
  }

  public TextRange getNewRange() {
    return getRange(myNewRanges);
  }

  public TextRange[] getOldRanges() {
    return myOldRanges;
  }

  public TextRange[] getNewRanges() {
    return myNewRanges;
  }

  private static TextRange[] getRanges(int[] starts, int[] ends) {
    if (starts.length == 0) {
      return EMPTY_RANGES;
    }
    final TextRange[] ranges = new TextRange[starts.length];
    for (int i = 0; i < starts.length; ++i) {
      ranges[i] = new TextRange(starts[i], ends[i]);
    }
    return ranges;
  }

  private static TextRange getRange(TextRange[] ranges) {
    if (ranges.length == 0) {
      return TextRange.EMPTY_RANGE;
    }
    return new TextRange(ranges[0].getStartOffset(), ranges[ranges.length - 1].getEndOffset());
  }
}
