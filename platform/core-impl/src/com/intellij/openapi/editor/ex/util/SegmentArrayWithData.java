/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import org.jetbrains.annotations.NotNull;

/**
 * Expands {@link SegmentArray} contract in providing ability to attach additional {@code 'short'} variable to target segment,
 * i.e. holds mappings like {@code 'index <-> (data, (start; end))'}.
 * <p/>
 * Not thread-safe.
 */
public class SegmentArrayWithData extends SegmentArray {
  private short[] myData;

  public SegmentArrayWithData() {
    myData = new short[INITIAL_SIZE];
  }

  public void setElementAt(int i, int startOffset, int endOffset, int data) {
    dataRangeCheck(data);
    setElementAt(i, startOffset, endOffset);
    myData = reallocateArray(myData, i+1);
    myData[i] = (short)data;
  }

  private static void dataRangeCheck(int data) {
    if (data < Short.MIN_VALUE || data > Short.MAX_VALUE) {
      throw new IndexOutOfBoundsException("data out of short range: " + data);
    }
  }

  @Override
  public void remove(int startIndex, int endIndex) {
    myData = remove(myData, startIndex, endIndex);
    super.remove(startIndex, endIndex);
  }

  public void replace(int startIndex, int endIndex, @NotNull SegmentArrayWithData newData) {
    int oldLen = endIndex - startIndex;
    int newLen = newData.getSegmentCount();

    int delta = newLen - oldLen;
    if (delta < 0) {
      remove(endIndex + delta, endIndex);
    }
    else if (delta > 0) {
      SegmentArrayWithData deltaData = new SegmentArrayWithData();
      for (int i = oldLen; i < newLen; i++) {
        deltaData.setElementAt(i - oldLen, newData.getSegmentStart(i), newData.getSegmentEnd(i), newData.getSegmentData(i));
      }
      insert(deltaData, startIndex + oldLen);
    }

    int common = Math.min(newLen, oldLen);
    replace(startIndex, newData, common);
  }


  protected void replace(int startOffset, @NotNull SegmentArrayWithData data, int len) {
    System.arraycopy(data.myData, 0, myData, startOffset, len);
    super.replace(startOffset, data, len);
  }

  public void insert(@NotNull SegmentArrayWithData segmentArray, int startIndex) {
    myData = insert(myData, segmentArray.myData, startIndex, segmentArray.getSegmentCount());
    super.insert(segmentArray, startIndex);
  }

  @NotNull
  private short[] insert(@NotNull short[] array, @NotNull short[] insertArray, int startIndex, int insertLength) {
    short[] newArray = reallocateArray(array, mySegmentCount + insertLength);
    if (startIndex < mySegmentCount) {
      System.arraycopy(newArray, startIndex, newArray, startIndex + insertLength, mySegmentCount - startIndex);
    }
    System.arraycopy(insertArray, 0, newArray, startIndex, insertLength);
    return newArray;
  }

  @NotNull
  private short[] remove(@NotNull short[] array, int startIndex, int endIndex) {
    if (endIndex < mySegmentCount) {
      System.arraycopy(array, endIndex, array, startIndex, mySegmentCount - endIndex);
    }
    return array;
  }

  public short getSegmentData(int index) {
    if(index < 0 || index >= mySegmentCount) {
      throw new IndexOutOfBoundsException("Wrong index: " + index);
    }
    return myData[index];
  }

  public void setSegmentData(int index, int data) {
    if(index < 0 || index >= mySegmentCount) throw new IndexOutOfBoundsException("Wrong index: " + index);
    dataRangeCheck(data);
    myData[index] = (short)data;
  }

  @NotNull
  private static short[] reallocateArray(@NotNull short[] array, int index) {
    if (index < array.length) return array;

    short[] newArray = new short[calcCapacity(array.length, index)];
    System.arraycopy(array, 0, newArray, 0, array.length);
    return newArray;
  }

  public SegmentArrayWithData copy() {
    final SegmentArrayWithData sa = new SegmentArrayWithData();
    sa.mySegmentCount = this.mySegmentCount;
    sa.myStarts = this.myStarts.clone();
    sa.myEnds = this.myEnds.clone();
    sa.myData = this.myData.clone();
    return sa;
  }
}

