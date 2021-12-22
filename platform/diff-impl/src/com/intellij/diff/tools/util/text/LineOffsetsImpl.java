// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.util.text;

import com.intellij.openapi.util.text.StringUtil;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

final class LineOffsetsImpl implements LineOffsets {
  @NotNull
  public static LineOffsets create(@NotNull CharSequence text) {
    IntList ends = new IntArrayList();

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

    return new LineOffsetsImpl(ends.toIntArray(), text.length());
  }

  private final int[] myLineEnds;
  private final int myTextLength;

  private LineOffsetsImpl(int[] lineEnds, int textLength) {
    myLineEnds = lineEnds;
    myTextLength = textLength;
  }

  @Override
  public int getLineStart(int line) {
    checkLineIndex(line);
    if (line == 0) return 0;
    return myLineEnds[line - 1] + 1;
  }

  @Override
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

  @Override
  public int getLineCount() {
    return myLineEnds.length;
  }

  @Override
  public int getTextLength() {
    return myTextLength;
  }

  private void checkLineIndex(int index) {
    if (index < 0 || index >= getLineCount()) {
      throw new IndexOutOfBoundsException("Wrong line: " + index + ". Available lines count: " + getLineCount());
    }
  }
}
