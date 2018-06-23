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

import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.text.StringUtil;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class LineOffsetsUtil {
  public static LineOffsets create(@NotNull Document document) {
    return new LineOffsetsDocumentWrapper(document);
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

    return new LineOffsetsImpl(ends.toNativeArray(), text.length());
  }

  private static class LineOffsetsImpl implements LineOffsets {
    private final int[] myLineEnds;
    private final int myTextLength;

    private LineOffsetsImpl(int[] lineEnds, int textLength) {
      myLineEnds = lineEnds;
      myTextLength = textLength;
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

    @Override
    public int getLineEnd(int line, boolean includeNewline) {
      checkLineIndex(line);
      return myLineEnds[line] + (includeNewline && line != myLineEnds.length - 1 ? 1 : 0);
    }

    @Override
    public int getLineNumber(int offset) {
      if (offset < 0 || offset > getTextLength()) {
        throw new IndexOutOfBoundsException("Wrong offset: " + offset + ". Available text length: " + getTextLength());
      }
      if (offset == 0) return 0;
      if (offset == getTextLength()) return getLineCount() - 1;

      int bsResult = Arrays.binarySearch(myLineEnds, offset);
      return bsResult >= 0 ? bsResult : -bsResult - 1;
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
  }

  private static class LineOffsetsDocumentWrapper implements LineOffsets {
    @NotNull private final Document myDocument;

    public LineOffsetsDocumentWrapper(@NotNull Document document) {
      myDocument = document;
    }

    @Override
    public int getLineStart(int line) {
      return myDocument.getLineStartOffset(line);
    }

    @Override
    public int getLineEnd(int line) {
      return myDocument.getLineEndOffset(line);
    }

    @Override
    public int getLineEnd(int line, boolean includeNewline) {
      if (myDocument.getLineCount() == 0) return 0;
      return myDocument.getLineEndOffset(line) + (includeNewline ? myDocument.getLineSeparatorLength(line) : 0);
    }

    @Override
    public int getLineNumber(int offset) {
      return myDocument.getLineNumber(offset);
    }

    @Override
    public int getLineCount() {
      return DiffUtil.getLineCount(myDocument);
    }

    @Override
    public int getTextLength() {
      return myDocument.getTextLength();
    }
  }
}
