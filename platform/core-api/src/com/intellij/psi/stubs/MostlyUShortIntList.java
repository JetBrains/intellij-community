// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs;

import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.ApiStatus;

import java.util.function.IntUnaryOperator;

/** An int list where most values are in the range 0..2^16 */
@ApiStatus.Internal
public final class MostlyUShortIntList implements IntUnaryOperator {
  private static final int IN_MAP = Character.MAX_VALUE;
  private char[] myData; // use char as an unsigned short
  private int mySize;
  private StrippedIntOpenHashMap myMap;

  MostlyUShortIntList(int initialCapacity) {
    myData = new char[initialCapacity];
  }

  private void ensureCapacity(int minCapacity) {
    int oldCapacity = myData.length;
    if (minCapacity > oldCapacity){
      char[] oldData = myData;
      int newCapacity = oldCapacity * 3 / 2 + 1;
      if (newCapacity < minCapacity){
        newCapacity = minCapacity;
      }
      myData = new char[newCapacity];
      System.arraycopy(oldData, 0, myData, 0, mySize);
    }
  }

  void add(int value) {
    if (value < 0 || value >= IN_MAP) {
      initMap().put(mySize, value);
      value = IN_MAP;
    }
    ensureCapacity(mySize + 1);
    myData[mySize++] = (char)value;
  }

  void set(int index, int value) {
    if (value < 0 || value >= IN_MAP) {
      initMap().put(index, value);
      value = IN_MAP;
    }
    myData[index] = (char)value;
  }

  private StrippedIntOpenHashMap initMap() {
    if (myMap == null) {
      myMap = new StrippedIntOpenHashMap();
    }
    return myMap;
  }

  @Override
  public int applyAsInt(int index) {
    return get(index);
  }

  public int get(int index) {
    int value = myData[index];
    return value == IN_MAP ? myMap.get(index, 0) : value;
  }

  int size() {
    return mySize;
  }

  void trimToSize() {
    if (mySize < myData.length){
      myData = ArrayUtil.realloc(myData, mySize);
    }
  }
}