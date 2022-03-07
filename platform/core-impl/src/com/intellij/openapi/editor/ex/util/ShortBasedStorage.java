// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.ex.util;

import com.intellij.psi.tree.IElementType;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.editor.ex.util.SegmentArray.INITIAL_SIZE;
import static com.intellij.openapi.editor.ex.util.SegmentArray.calcCapacity;

/**
 * SegmentArrayWithData storage based on the short array. It allows to store one short per segment which is enough for storing
 * {@link IElementType} index and and restartability of the state (positive values are for initial state).
 */
public class ShortBasedStorage implements DataStorage {
  protected short[] myData;

  public ShortBasedStorage() {
    myData = new short[INITIAL_SIZE];
  }

  protected ShortBasedStorage(short[] data) {
    myData = data;
  }

  @Override
  public void setData(int segmentIndex, int data) {
    if (segmentIndex >= myData.length) {
      myData = ArrayUtil.realloc(myData, calcCapacity(myData.length, segmentIndex));
    }
    myData[segmentIndex] = (short)data;
  }

  @Override
  public void remove(int startIndex, int endIndex, int mySegmentCount) {
    if (endIndex < mySegmentCount) {
      System.arraycopy(myData, endIndex, myData, startIndex, mySegmentCount - endIndex);
    }
  }

  @Override
  public void replace(@NotNull DataStorage storage, int startOffset, int len) {
    assert storage instanceof ShortBasedStorage;
    System.arraycopy(((ShortBasedStorage)storage).myData, 0, myData, startOffset, len);
  }

  @Override
  public void insert(@NotNull DataStorage storageToInsert, int startIndex, int segmentCountToInsert, int segmentCount) {
    assert storageToInsert instanceof ShortBasedStorage;
    myData = insert(myData, ((ShortBasedStorage)storageToInsert).myData, startIndex, segmentCountToInsert, segmentCount);
  }

  @Override
  public int getData(int index) {
    return myData[index];
  }

  @Override
  public int packData(@NotNull IElementType tokenType, int state, boolean isRestartableState) {
    final short idx = tokenType.getIndex();
    return isRestartableState ? idx : -idx;
  }

  @Override
  public int unpackStateFromData(int data) {
    throw new UnsupportedOperationException("Unable to unpack state, state is not stored in ShortBasedStorage");
  }

  @Override
  public @NotNull IElementType unpackTokenFromData(int data) {
    return IElementType.find((short)Math.abs(data));
  }

  @Override
  public @NotNull DataStorage copy() {
    return new ShortBasedStorage(myData);
  }

  @Override
  public @NotNull DataStorage createStorage() {
    return new ShortBasedStorage();
  }

  protected static short @NotNull [] insert(short @NotNull [] array,
                                            short @NotNull [] insertArray, int startIndex, int insertLength, int mySegmentCount) {
    short[] newArray = reallocateArray(array, mySegmentCount + insertLength);
    if (startIndex < mySegmentCount) {
      System.arraycopy(newArray, startIndex, newArray, startIndex + insertLength, mySegmentCount - startIndex);
    }
    System.arraycopy(insertArray, 0, newArray, startIndex, insertLength);
    return newArray;
  }

  protected static short @NotNull [] reallocateArray(short @NotNull [] array, int index) {
    if (index < array.length) return array;
    return ArrayUtil.realloc(array, calcCapacity(array.length, index));
  }
}
