/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.editor.ex.LineIterator;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.util.BitUtil;
import com.intellij.util.containers.IntArrayList;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.text.MergingCharSequence;
import gnu.trove.TByteArrayList;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.Arrays;

/**
 * Data structure specialized for working with document text lines, i.e. stores information about line mapping to document
 * offsets and provides convenient ways to work with that information like retrieving target line by document offset etc.
 * <p/>
 * Immutable.
 */
public class LineSet{
  private static final int MODIFIED_MASK = 0x4;
  private static final int SEPARATOR_MASK = 0x3;

  private final int[] myStarts;
  private final byte[] myFlags; // MODIFIED_MASK bit is for is/setModified(line); SEPARATOR_MASK 2 bits stores line separator length: 0..2
  private final int myLength;

  private LineSet(int[] starts, byte[] flags, int length) {
    myStarts = starts;
    myFlags = flags;
    myLength = length;
  }

  public static LineSet createLineSet(CharSequence text) {
    return createLineSet(text, false);
  }

  @NotNull
  private static LineSet createLineSet(@NotNull CharSequence text, boolean markModified) {
    TIntArrayList starts = new TIntArrayList();
    TByteArrayList flags = new TByteArrayList();

    LineTokenizer lineTokenizer = new LineTokenizer(text);
    while (!lineTokenizer.atEnd()) {
      starts.add(lineTokenizer.getOffset());
      flags.add((byte) (lineTokenizer.getLineSeparatorLength() | (markModified ? MODIFIED_MASK : 0)));
      lineTokenizer.advance();
    }
    return new LineSet(starts.toNativeArray(), flags.toNativeArray(), text.length());
  }

  @NotNull
  LineSet update(@NotNull CharSequence prevText, int start, int end, @NotNull CharSequence replacement, boolean wholeTextReplaced) {
    if (myLength == 0) {
      return createLineSet(replacement, !wholeTextReplaced);
    }

    LineSet result = isSingleLineChange(start, end, replacement)
                     ? updateInsideOneLine(findLineIndex(start), replacement.length() - (end - start))
                     : genericUpdate(prevText, start, end, replacement);

    if (doTest) {
      MergingCharSequence newText = new MergingCharSequence(
        new MergingCharSequence(prevText.subSequence(0, start), replacement),
        prevText.subSequence(end, prevText.length()));
      result.checkEquals(createLineSet(newText));
    }
    return wholeTextReplaced ? result.clearModificationFlags() : result;
  }

  private boolean isSingleLineChange(int start, int end, @NotNull CharSequence replacement) {
    if (start == 0 && end == myLength && replacement.length() == 0) return false;

    int startLine = findLineIndex(start);
    return startLine == findLineIndex(end) && !CharArrayUtil.containLineBreaks(replacement) && !isLastEmptyLine(startLine);
  }

  @NotNull
  private LineSet updateInsideOneLine(int line, int lengthDelta) {
    int[] starts = myStarts.clone();
    for (int i = line + 1; i < starts.length; i++) {
      starts[i] += lengthDelta;
    }

    byte[] flags = myFlags.clone();
    flags[line] |= MODIFIED_MASK;
    return new LineSet(starts, flags, myLength + lengthDelta);
  }

  private LineSet genericUpdate(CharSequence prevText, int _start, int _end, CharSequence replacement) {
    int startOffset = _start;
    if (replacement.length() > 0 && replacement.charAt(0) == '\n' && startOffset > 0 && prevText.charAt(startOffset - 1) == '\r') {
      startOffset--;
    }
    int startLine = findLineIndex(startOffset);
    startOffset = getLineStart(startLine);

    int endOffset = _end;
    if (replacement.length() > 0 && replacement.charAt(replacement.length() - 1) == '\r' && endOffset < prevText.length() && prevText.charAt(endOffset) == '\n') {
      endOffset++;
    }
    int endLine = findLineIndex(endOffset);
    endOffset = getLineEnd(endLine);
    if (!isLastEmptyLine(endLine)) endLine++;

    if (startOffset < _start) {
      replacement = new MergingCharSequence(prevText.subSequence(startOffset, _start), replacement);
    }
    if (_end < endOffset) {
      replacement = new MergingCharSequence(replacement, prevText.subSequence(_end, endOffset));
    }

    LineSet patch = createLineSet(replacement, true);
    return applyPatch(startOffset, endOffset, startLine, endLine, patch);
  }

  private void checkEquals(@NotNull LineSet fresh) {
    if (getLineCount() != fresh.getLineCount()) {
      throw new AssertionError();
    }
    for (int i = 0; i < getLineCount(); i++) {
      boolean start = getLineStart(i) != fresh.getLineStart(i);
      boolean end = getLineEnd(i) != fresh.getLineEnd(i);
      boolean sep = getSeparatorLength(i) != fresh.getSeparatorLength(i);
      if (start || end || sep) {
        throw new AssertionError();
      }
    }
  }

  @NotNull
  private LineSet applyPatch(int startOffset, int endOffset, int startLine, int endLine, @NotNull LineSet patch) {
    int lineShift = patch.myStarts.length - (endLine - startLine);
    int lengthShift = patch.myLength - (endOffset - startOffset);

    int newLineCount = myStarts.length + lineShift;
    int[] starts = new int[newLineCount];
    byte[] flags = new byte[newLineCount];

    for (int i = 0; i < startLine; i++) {
      starts[i] = myStarts[i];
      flags[i] = myFlags[i];
    }
    for (int i = 0; i < patch.myStarts.length; i++) {
      starts[startLine + i] = patch.myStarts[i] + startOffset;
      flags[startLine + i] = patch.myFlags[i];
    }
    for (int i = endLine; i < myStarts.length; i++) {
      starts[lineShift + i] = myStarts[i] + lengthShift;
      flags[lineShift + i] = myFlags[i];
    }
    return new LineSet(starts, flags, myLength + lengthShift);
  }

  public int findLineIndex(int offset) {
    if (offset < 0 || offset > myLength) {
      throw new IndexOutOfBoundsException("Wrong offset: " + offset + ". Should be in range: [0, " + myLength + "]");
    }
    if (myLength == 0) return 0;
    if (offset == myLength) return getLineCount() - 1;

    int bsResult = Arrays.binarySearch(myStarts, offset);
    return bsResult >= 0 ? bsResult : -bsResult - 2;
  }

  @NotNull
  public LineIterator createIterator() {
    return new LineIteratorImpl(this);
  }

  public final int getLineStart(int index) {
    checkLineIndex(index);
    return isLastEmptyLine(index) ? myLength : myStarts[index];
  }

  private boolean isLastEmptyLine(int index) {
    return index == myFlags.length && index > 0 && getSeparatorLengthUnsafe(index - 1) > 0;
  }

  public final int getLineEnd(int index) {
    checkLineIndex(index);
    return index >= myStarts.length - 1 ? myLength : myStarts[index + 1];
  }

  private void checkLineIndex(int index) {
    if (index < 0 || index >= getLineCount()) {
      throw new IndexOutOfBoundsException("Wrong line: " + index + ". Available lines count: " + getLineCount());
    }
  }

  final boolean isModified(int index) {
    checkLineIndex(index);
    return !isLastEmptyLine(index) && BitUtil.isSet(myFlags[index], MODIFIED_MASK);
  }

  @NotNull
  final LineSet setModified(@NotNull IntArrayList indices) {
    if (indices.isEmpty()) {
      return this;
    }
    if (indices.size() == 1) {
      int index = indices.get(0);
      if (isLastEmptyLine(index) || isModified(index)) return this;
    }

    byte[] flags = myFlags.clone();
    for (int i=0; i<indices.size();i++) {
      int index = indices.get(i);
      flags[index] |= MODIFIED_MASK;
    }
    return new LineSet(myStarts, flags, myLength);
  }

  @NotNull
  LineSet clearModificationFlags(int startLine, int endLine) {
    if (startLine > endLine) {
      throw new IllegalArgumentException("endLine < startLine: " + endLine + " < " + startLine + "; lineCount: " + getLineCount());
    }
    checkLineIndex(startLine);
    checkLineIndex(endLine - 1);

    if (isLastEmptyLine(endLine - 1)) endLine--;
    if (startLine >= endLine) return this;

    byte[] flags = myFlags.clone();
    for (int i = startLine; i < endLine; i++) {
      flags[i] &= ~MODIFIED_MASK;
    }
    return new LineSet(myStarts, flags, myLength);
  }

  @NotNull
  LineSet clearModificationFlags() {
    return getLineCount() == 0 ? this : clearModificationFlags(0, getLineCount());
  }

  final int getSeparatorLength(int index) {
    checkLineIndex(index);
    return getSeparatorLengthUnsafe(index);
  }

  private int getSeparatorLengthUnsafe(int index) {
    return index < myFlags.length ? myFlags[index] & SEPARATOR_MASK : 0;
  }

  final int getLineCount() {
    return myStarts.length + (isLastEmptyLine(myStarts.length) ? 1 : 0);
  }

  @TestOnly
  public static void setTestingMode(boolean testMode) {
    doTest = testMode;
  }

  private static boolean doTest;

  int getLength() {
    return myLength;
  }
}
