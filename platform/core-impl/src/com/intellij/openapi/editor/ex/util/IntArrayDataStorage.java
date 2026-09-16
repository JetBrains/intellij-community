// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex.util;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * A {@link DataStorage} that keeps one int per segment in an int array.
 * <p>
 * The class owns the array and the segment operations on it. A subclass adds the packing of the
 * {@link com.intellij.psi.tree.IElementType} and of the lexer state into that int.
 */
@ApiStatus.Internal
public abstract class IntArrayDataStorage implements DataStorage {

  protected int[] myData;

  protected IntArrayDataStorage() {
    this(new int[SegmentArray.INITIAL_SIZE]);
  }

  protected IntArrayDataStorage(int @NotNull [] data) {
    myData = data;
  }

  @Override
  public void setData(int segmentIndex, int data) {
    myData = SegmentArrayWithData.reallocateArray(myData, segmentIndex + 1);
    myData[segmentIndex] = data;
  }

  @Override
  public void remove(int startIndex, int endIndex, int mySegmentCount) {
    if (endIndex < mySegmentCount) {
      System.arraycopy(myData, endIndex, myData, startIndex, mySegmentCount - endIndex);
    }
  }

  @Override
  public void replace(@NotNull DataStorage storage, int startOffset, int len) {
    System.arraycopy(((IntArrayDataStorage)storage).myData, 0, myData, startOffset, len);
  }

  @Override
  public void insert(@NotNull DataStorage storageToInsert, int startIndex, int segmentCountToInsert, int segmentCount) {
    myData = SegmentArray.insert(myData, ((IntArrayDataStorage)storageToInsert).myData, startIndex, segmentCountToInsert, segmentCount);
  }

  @Override
  public int getData(int index) {
    return myData[index];
  }
}
