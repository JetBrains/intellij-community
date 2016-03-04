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
package com.intellij.openapi.editor.ex.util;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

/**
 * This class is a data structure specialized for working with the indexed segments, i.e. it holds numerous mappings like
 * {@code 'index <-> (start; end)'} and provides convenient way for working with them, e.g. find index by particular offset that
 * belongs to target <code>(start; end)</code> segment etc.
 * <p/>
 * Not thread-safe.
 */
public class SegmentArray {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.ex.util.SegmentArray");
  protected int[] myStarts;
  protected int[] myEnds;

  protected int mySegmentCount;
  protected static final int INITIAL_SIZE = 64;

  protected SegmentArray() {
    myStarts = new int[INITIAL_SIZE];
    myEnds = new int[INITIAL_SIZE];
  }

  protected void setElementAt(int i, int startOffset, int endOffset) {
    if (startOffset < 0) {
      LOG.error("Invalid startOffset:" + startOffset);
    }
    if (endOffset < 0) {
      LOG.error("Invalid endOffset:" + endOffset);
    }

    if (i >= mySegmentCount) {
      mySegmentCount = i + 1;
    }

    myStarts = reallocateArray(myStarts, i);
    myStarts[i] = startOffset;

    myEnds = reallocateArray(myEnds, i);
    myEnds[i] = endOffset;
  }

  protected void replace(int startOffset, @NotNull SegmentArray data, int len) {
    System.arraycopy(data.myStarts, 0, myStarts, startOffset, len);
    System.arraycopy(data.myEnds, 0, myEnds, startOffset, len);
  }

  static int calcCapacity(int currentArraySize, int index) {
    if (currentArraySize == 0) {
      currentArraySize = 16;
    }
    else {
      currentArraySize += currentArraySize / 5; // avoid overflow
    }
    if (index >= currentArraySize) {
      currentArraySize = index + index / 5; // avoid overflow
    }
    return currentArraySize;
  }

  @NotNull
  private static int[] reallocateArray(@NotNull int[] array, int index) {
    if (index < array.length) return array;

    int[] newArray = new int[calcCapacity(array.length, index)];
    System.arraycopy(array, 0, newArray, 0, array.length);
    return newArray;
  }

  protected int noSegmentsAvailable(int offset) {
    throw new IllegalStateException("no segments available. offset = " + offset);
  }

  protected int offsetOutOfRange(int offset, int lastValidOffset) {
    throw new IndexOutOfBoundsException("Wrong offset: " + offset + ". Should be in range: [0, " + lastValidOffset + "]");
  }

  /**
   * @throws IllegalStateException if a gap between segments is detected, or if there are no segments and an index for a positive offset is
   * requested
   */
  public final int findSegmentIndex(int offset) {
    if (mySegmentCount <= 0) {
      return offset == 0 ? 0 : noSegmentsAvailable(offset);
    }

    final int lastValidOffset = getLastValidOffset();
    if (offset > lastValidOffset || offset < 0) {
      return offsetOutOfRange(offset, lastValidOffset);
    }

    int end = mySegmentCount - 1;
    if (offset == lastValidOffset) {
      return end;
    }

    int start = 0;
    while (start <= end) {
      int i = (start + end) >>> 1;
      if (offset < myStarts[i]) {
        end = i - 1;
      }
      else if (offset >= myEnds[i]) {
        start = i + 1;
      }
      else {
        return i;
      }
    }

    return segmentNotFound(offset, start);
  }

  protected int segmentNotFound(int offset, int start) {
    // This means that there is a gap at given offset
    if (offset < myStarts[start] || offset >= myEnds[start]) {
      throw new IllegalStateException("Gap at offset " + offset + " near segment " + start);
    } 
    return start;
  }

  public int getLastValidOffset() {
    return mySegmentCount == 0 ? 0 : myEnds[mySegmentCount - 1];
  }

  public final void changeSegmentLength(int startIndex, int change) {
    if (startIndex >= 0 && startIndex < mySegmentCount) {
      myEnds[startIndex] += change;
    }
    shiftSegments(startIndex + 1, change);
  }

  public final void shiftSegments(int startIndex, int shift) {
    for (int i = startIndex; i < mySegmentCount; i++) {
      myStarts[i] += shift;
      myEnds[i] += shift;
      if (myStarts[i] < 0 || myEnds[i] < 0) {
        LOG.error("Error shifting segments: myStarts[" + i + "] = " + myStarts[i] + ", myEnds[" + i + "] = " + myEnds[i]);
      }
    }
  }

  public void removeAll() {
    mySegmentCount = 0;
  }

  public void remove(int startIndex, int endIndex) {
    myStarts = remove(myStarts, startIndex, endIndex);
    myEnds = remove(myEnds, startIndex, endIndex);
    mySegmentCount -= endIndex - startIndex;
  }

  @NotNull
  protected int[] remove(@NotNull int[] array, int startIndex, int endIndex) {
    if (endIndex < mySegmentCount) {
      System.arraycopy(array, endIndex, array, startIndex, mySegmentCount - endIndex);
    }
    return array;
  }

  protected void insert(@NotNull SegmentArray segmentArray, int startIndex) {
    myStarts = insert(myStarts, segmentArray.myStarts, startIndex, segmentArray.getSegmentCount());
    myEnds = insert(myEnds, segmentArray.myEnds, startIndex, segmentArray.getSegmentCount());
    mySegmentCount += segmentArray.getSegmentCount();
  }

  @NotNull
  protected int[] insert(@NotNull int[] array, @NotNull int[] insertArray, int startIndex, int insertLength) {
    int[] newArray = reallocateArray(array, mySegmentCount + insertLength);
    if (startIndex < mySegmentCount) {
      System.arraycopy(newArray, startIndex, newArray, startIndex + insertLength, mySegmentCount - startIndex);
    }
    System.arraycopy(insertArray, 0, newArray, startIndex, insertLength);
    return newArray;
  }

  public int getSegmentStart(int index) {
    if (index < 0 || index >= mySegmentCount) {
      throw new IndexOutOfBoundsException("Wrong line: " + index + ". Available lines count: " + mySegmentCount);
    }
    return myStarts[index];
  }

  public int getSegmentEnd(int index) {
    if (index < 0 || index >= mySegmentCount) {
      throw new IndexOutOfBoundsException("Wrong line: " + index + ". Available lines count: " + mySegmentCount);
    }
    return myEnds[index];
  }


  public int getSegmentCount() {
    return mySegmentCount;
  }
}

