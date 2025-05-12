// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.ex.LineIterator;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.BitUtil;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.text.MergingCharSequence;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.bytes.ByteList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.Arrays;

/**
 * Data structure specialized for working with document text lines, i.e. stores information about line mapping to document
 * offsets and provides convenient ways to work with that information like retrieving target line by document offset etc.
 * <p/>
 * Immutable.
 */
@ApiStatus.Internal
public final class LineSet {
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

  private static @NotNull LineSet createLineSet(@NotNull CharSequence text, boolean markModified) {
    IntList starts = new IntArrayList();
    ByteList flags = new ByteArrayList();

    LineTokenizer lineTokenizer = new LineTokenizer(text);
    while (!lineTokenizer.atEnd()) {
      starts.add(lineTokenizer.getOffset());
      flags.add((byte) (lineTokenizer.getLineSeparatorLength() | (markModified ? MODIFIED_MASK : 0)));
      lineTokenizer.advance();
    }
    return new LineSet(starts.toIntArray(), flags.toByteArray(), text.length());
  }

  @VisibleForTesting
  public @NotNull LineSet update(@NotNull CharSequence prevText, int start, int end, @NotNull CharSequence replacement, boolean wholeTextReplaced) {
    if (myLength == 0) {
      return createLineSet(replacement, !wholeTextReplaced);
    }

    // if we're breaking or creating a '\r\n' pair, expand the changed range to include it fully
    CharSequence newText = StringUtil.replaceSubSequence(prevText, start, end, replacement);
    if (hasChar(prevText, start - 1, '\r') &&
        (hasChar(prevText, start, '\n') || hasChar(newText, start, '\n'))) {
      replacement = new MergingCharSequence("\r", replacement);
      start--;
    }

    if (hasChar(prevText, end, '\n') &&
        (hasChar(prevText, end -1, '\r') || hasChar(newText, start + replacement.length() - 1, '\r'))) {
      replacement = new MergingCharSequence(replacement, "\n");
      end++;
    }

    LineSet result = isSingleLineChange(start, end, replacement)
                     ? updateInsideOneLine(findLineIndex(start), replacement.length() - (end - start))
                     : genericUpdate(start, end, replacement);

    return wholeTextReplaced ? result.clearModificationFlags() : result;
  }

  private static boolean hasChar(CharSequence s, int index, char c) {
    return index >= 0 && index < s.length() && s.charAt(index) == c;
  }

  private boolean isSingleLineChange(int start, int end, @NotNull CharSequence replacement) {
    if (start == 0 && end == myLength && replacement.length() == 0) return false;

    int startLine = findLineIndex(start);
    return startLine == findLineIndex(end) && !CharArrayUtil.containLineBreaks(replacement) && !isLastEmptyLine(startLine);
  }

  private @NotNull LineSet updateInsideOneLine(int line, int lengthDelta) {
    int[] starts = myStarts.clone();
    for (int i = line + 1; i < starts.length; i++) {
      starts[i] += lengthDelta;
    }

    byte[] flags = myFlags.clone();
    flags[line] |= MODIFIED_MASK;
    return new LineSet(starts, flags, myLength + lengthDelta);
  }

  private LineSet genericUpdate(int startOffset, int endOffset, CharSequence replacement) {
    int startLine = findLineIndex(startOffset);
    int endLine = findLineIndex(endOffset);

    LineSet patch = createLineSet(replacement, true);

    int lengthShift = patch.myLength - (endOffset - startOffset);

    int startLineStart = getLineStart(startLine);
    boolean addStartLine = startOffset - startLineStart > 0 || patch.myStarts.length > 0 || endOffset < myLength;
    boolean addEndLine = endOffset < myLength && patch.myLength > 0 && patch.getSeparatorLength(patch.myStarts.length - 1) > 0;
    int newLineCount = startLine + (addStartLine ? 1 : 0) +
                       Math.max(patch.myStarts.length - 1, 0) +
                       (addEndLine ? 1 : 0) + Math.max(myStarts.length - endLine - 1, 0);

    int[] starts = ArrayUtil.newIntArray(newLineCount);
    byte[] flags = ArrayUtil.newByteArray(newLineCount);

    if (startLine > 0) {
      System.arraycopy(myStarts, 0, starts, 0, startLine);
      System.arraycopy(myFlags, 0, flags, 0, startLine);
    }

    int toIndex = startLine;
    if (addStartLine) {
      starts[toIndex] = startLineStart;
      flags[toIndex] = patch.myStarts.length > 0 ? patch.myFlags[0] : MODIFIED_MASK;
      toIndex++;
    }

    toIndex = patch.shiftData(starts, flags, 1, toIndex, patch.myStarts.length - 1, startOffset);

    if (endOffset < myLength) {
      if (addEndLine) {
        starts[toIndex] = endOffset + lengthShift;
        flags[toIndex] = (byte) (myFlags[endLine] | MODIFIED_MASK);
        toIndex++;
      } else if (toIndex > 0) {
        flags[toIndex - 1] = (byte) (myFlags[endLine] | MODIFIED_MASK);
      }
    }

    shiftData(starts, flags, endLine + 1, toIndex, myStarts.length - (endLine + 1), lengthShift);

    return new LineSet(starts, flags, myLength + lengthShift);
  }

  private int shiftData(int[] dstStarts, byte[] dstFlags, int srcOffset, int dstOffset, int count, int offsetDelta) {
    if (count < 0) return dstOffset;

    System.arraycopy(myFlags, srcOffset, dstFlags, dstOffset, count);
    for (int i = 0; i < count; i++) {
      dstStarts[dstOffset + i] = myStarts[srcOffset + i] + offsetDelta;
    }
    return dstOffset + count;
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

  public @NotNull LineIterator createIterator() {
    return new LineIteratorImpl(this);
  }

  public int getLineStart(int index) {
    checkLineIndex(index);
    return isLastEmptyLine(index) ? myLength : myStarts[index];
  }

  private boolean isLastEmptyLine(int index) {
    return index == myFlags.length && hasEol(index - 1);
  }

  private boolean hasEol(int lineIndex) {
    return lineIndex >= 0 && getSeparatorLengthUnsafe(lineIndex) > 0;
  }

  public int getLineEnd(int index) {
    checkLineIndex(index);
    return index >= myStarts.length - 1 ? myLength : myStarts[index + 1];
  }

  private void checkLineIndex(int index) {
    if (index < 0 || index >= getLineCount()) {
      throw new IndexOutOfBoundsException("Wrong line: " + index + ". Available lines count: " + getLineCount());
    }
  }

  boolean isModified(int index) {
    checkLineIndex(index);
    return !isLastEmptyLine(index) && BitUtil.isSet(myFlags[index], MODIFIED_MASK);
  }

  @NotNull LineSet setModified(@NotNull IntList indices) {
    if (indices.isEmpty()) {
      return this;
    }
    if (indices.size() == 1) {
      int index = indices.getInt(0);
      if (isLastEmptyLine(index) || isModified(index)) return this;
    }

    byte[] flags = myFlags.clone();
    for (int i=0; i<indices.size();i++) {
      int index = indices.getInt(i);
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

  @VisibleForTesting
  public int getSeparatorLength(int index) {
    checkLineIndex(index);
    return getSeparatorLengthUnsafe(index);
  }

  private int getSeparatorLengthUnsafe(int index) {
    return index < myFlags.length ? myFlags[index] & SEPARATOR_MASK : 0;
  }

  public int getLineCount() {
    return myStarts.length + (isLastEmptyLine(myStarts.length) ? 1 : 0);
  }

  public int getLength() {
    return myLength;
  }
}
