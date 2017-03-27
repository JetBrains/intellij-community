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
package com.intellij.diff.tools.util.text;

import com.intellij.openapi.util.text.StringUtil;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;

public class LineOffsets {
  private final int[] myLineEnds;
  private final int myTextLength;

  private LineOffsets(int[] ends, int length) {
    myLineEnds = ends;
    myTextLength = length;
  }

  public int getLineStart(int line) {
    checkLineIndex(line);
    if (line == 0) return 0;
    return myLineEnds[line - 1] + 1;
  }

  public int getLineEnd(int line) {
    checkLineIndex(line);
    return myLineEnds[line];
  }

  public int getLineCount() {
    return myLineEnds.length;
  }

  public int getTextLength() {
    return myTextLength;
  }

  private void checkLineIndex(int index) {
    if (index < 0 || index >= getLineCount()) {
      throw new IndexOutOfBoundsException("Wrong line: " + index + ". Available lines count: " + getLineCount());
    }
  }

  @NotNull
  public static LineOffsets create(@NotNull CharSequence text) {
    TIntArrayList ends = new TIntArrayList();

    int index = 0;
    while (true) {
      int lineEnd = StringUtil.indexOf(text, '\n', index);
      if (lineEnd != -1) {
        ends.add(lineEnd);
        index = lineEnd + 1;
      }
      else {
        ends.add(text.length());
        break;
      }
    }

    return new LineOffsets(ends.toNativeArray(), text.length());
  }
}
