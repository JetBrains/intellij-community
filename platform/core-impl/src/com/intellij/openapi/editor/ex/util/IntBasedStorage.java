// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.ex.util;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.editor.ex.util.SegmentArray.INITIAL_SIZE;

/**
 * SegmentArrayWithData storage based on the int array. It allows to store one int per segment.
 * It allows to pack {@link IElementType} index and state of the lexer for segment.
 */
@ApiStatus.Internal
public class IntBasedStorage implements DataStorage {
  int[] myData;

  public IntBasedStorage() {
    myData = new int[INITIAL_SIZE];
  }

  private IntBasedStorage(int[] data) {
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
    assert storage instanceof IntBasedStorage;
    System.arraycopy(((IntBasedStorage)storage).myData, 0, myData, startOffset, len);
  }

  @Override
  public void insert(@NotNull DataStorage storageToInsert, int startIndex, int segmentCountToInsert, int segmentCount) {
    assert storageToInsert instanceof IntBasedStorage;
    myData = SegmentArray.insert(myData, ((IntBasedStorage)storageToInsert).myData, startIndex, segmentCountToInsert, segmentCount);
  }

  @Override
  public int getData(int index) {
    return myData[index];
  }

  @Override
  public int packData(@NotNull IElementType tokenType, int state, boolean isRestartableState) {
    return ((state & 0xFFFF) << 16) | (tokenType.getIndex() & 0xffff);
  }

  @Override
  public int unpackStateFromData(int data) {
    return data >> 16;
  }

  @Override
  public @NotNull IElementType unpackTokenFromData(int data) {
    return IElementType.find((short)(data & 0xffff));
  }

  @Override
  public @NotNull DataStorage copy() {
    return new IntBasedStorage(myData);
  }

  @Override
  public @NotNull DataStorage createStorage() {
    return new IntBasedStorage();
  }
}
