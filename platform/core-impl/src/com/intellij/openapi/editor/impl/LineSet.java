/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
  private final byte[] myFlags;
  private final int myLength;

  private LineSet(int[] starts, byte[] flags, int length) {
    myStarts = starts;
    myFlags = flags;
    myLength = length;
  }

  public static LineSet createLineSet(CharSequence text) {
    return createLineSet(text, false);
  }

  private static LineSet createLineSet(CharSequence text, boolean markModified) {
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

  LineSet update(CharSequence prevText, int _start, int _end, CharSequence replacement, boolean wholeTextReplaced) {
    if (myLength == 0) {
      return createLineSet(replacement, !wholeTextReplaced);
    }

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

    replacement = new MergingCharSequence(
      new MergingCharSequence(prevText.subSequence(startOffset, _start), replacement),
      prevText.subSequence(_end, endOffset));

    LineSet patch = createLineSet(replacement, true);
    LineSet applied = applyPatch(startOffset, endOffset, startLine, endLine, patch);
    if (doTest) {
      final MergingCharSequence newText = new MergingCharSequence(
        new MergingCharSequence(prevText.subSequence(0, startOffset), replacement),
        prevText.subSequence(endOffset, prevText.length()));
      applied.checkEquals(createLineSet(newText));
    }
    return wholeTextReplaced ? applied.clearModificationFlags() : applied;
  }

  private void checkEquals(LineSet fresh) {
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
  private LineSet applyPatch(int startOffset, int endOffset, int startLine, int endLine, LineSet patch) {
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

  public LineIterator createIterator() {
    return new LineIteratorImpl(this);
  }

  public final int getLineStart(int index) {
    checkLineIndex(index);
    return isLastEmptyLine(index) ? myLength : myStarts[index];
  }

  private boolean isLastEmptyLine(int index) {
    return index == myFlags.length && index > 0 && (myFlags[index - 1] & SEPARATOR_MASK) > 0;
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
    return !isLastEmptyLine(index) && (myFlags[index] & MODIFIED_MASK) != 0;
  }

  final LineSet setModified(int index) {
    if (isLastEmptyLine(index) || isModified(index)) return this;

    byte[] flags = myFlags.clone();
    flags[index] |= MODIFIED_MASK;
    return new LineSet(myStarts, flags, myLength);
  }

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

  LineSet clearModificationFlags() {
    byte[] flags = myFlags.clone();
    for (int i = 0; i < flags.length; i++) {
      flags[i] &= ~MODIFIED_MASK;
    }
    return new LineSet(myStarts, flags, myLength);
  }

  final int getSeparatorLength(int index) {
    checkLineIndex(index);
    return index < myFlags.length ? myFlags[index] & SEPARATOR_MASK : 0;
  }

  final int getLineCount() {
    return myStarts.length + (isLastEmptyLine(myStarts.length) ? 1 : 0);
  }

  @TestOnly
  public static void setTestingMode(boolean testMode) {
    doTest = testMode;
  }

  private static boolean doTest = false;

}
