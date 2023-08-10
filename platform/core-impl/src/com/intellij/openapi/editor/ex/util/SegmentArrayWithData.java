// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex.util;

import com.intellij.psi.tree.IElementType;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Expands {@link SegmentArray} contract by providing ability to attach additional data to target segment,
 * E.g., by holding mappings like {@code 'index <-> (data, (start; end))'}.
 * <p/>
 * Not thread-safe.
 */
public class SegmentArrayWithData extends SegmentArray {
  private DataStorage myStorage;

  public SegmentArrayWithData(@NotNull DataStorage storage) {
    myStorage = storage;
  }

  public @NotNull DataStorage createStorage() {
    return myStorage.createStorage();
  }

  public void setElementAt(int i, int startOffset, int endOffset, int data) {
    setElementAt(i, startOffset, endOffset);
    myStorage.setData(i, data);
  }


  @Override
  public void remove(int startIndex, int endIndex) {
    myStorage.remove(startIndex, endIndex, mySegmentCount);
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
      SegmentArrayWithData deltaData = new SegmentArrayWithData(myStorage.createStorage());
      for (int i = oldLen; i < newLen; i++) {
        deltaData.setElementAt(i - oldLen, newData.getSegmentStart(i), newData.getSegmentEnd(i), newData.getSegmentData(i));
      }
      insert(deltaData, startIndex + oldLen);
    }

    int common = Math.min(newLen, oldLen);
    replace(startIndex, newData, common);
  }


  protected void replace(int startOffset, @NotNull SegmentArrayWithData data, int len) {
    myStorage.replace(data.myStorage, startOffset, len);
    super.replace(startOffset, data, len);
  }

  public void insert(@NotNull SegmentArrayWithData segmentArray, int startIndex) {
    myStorage.insert(segmentArray.myStorage, startIndex, segmentArray.getSegmentCount(), mySegmentCount);
    super.insert(segmentArray, startIndex);
  }

  public int getSegmentData(int index) {
    if(index < 0 || index >= mySegmentCount) {
      throw new IndexOutOfBoundsException("Wrong index: " + index);
    }
    return myStorage.getData(index);
  }

  static int @NotNull [] reallocateArray(int @NotNull [] array, int index) {
    if (index < array.length) return array;
    return ArrayUtil.realloc(array, calcCapacity(array.length, index));
  }

  public @NotNull SegmentArrayWithData copy() {
    final SegmentArrayWithData sa = new SegmentArrayWithData(createStorage());
    sa.mySegmentCount = mySegmentCount;
    sa.myStarts = myStarts.clone();
    sa.myEnds = myEnds.clone();
    sa.myStorage = myStorage.copy();
    return sa;
  }

  /**
   * Unpacks state from segment data returned by
   *
   * @param data see {@link SegmentArrayWithData#getSegmentData(int)}
   * @return lexer state stored in data
   */
  public int unpackStateFromData(int data) {
    return myStorage.unpackStateFromData(data);
  }

  /**
   * Unpacks token type from segment data returned by
   * {@link SegmentArrayWithData#getSegmentData(int)}
   *
   * @param data to unpack
   * @return element type stored in data
   * @throws IndexOutOfBoundsException if encoded IElementType can not be found in IElementType registry
   */
  public @NotNull IElementType unpackTokenFromData(int data) {
    return myStorage.unpackTokenFromData(data);
  }

  /**
   * Packs tokenType and lexer state in data
   *
   * @param tokenType          lexer current token type
   * @param state              lexer current state
   * @param isRestartableState true if state is restartable
   * @return packed lexer state and tokenType in data
   */
  public int packData(@NotNull IElementType tokenType, int state, boolean isRestartableState) {
    return myStorage.packData(tokenType, state, isRestartableState);
  }
}

