// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.stubs;

import com.intellij.util.containers.UnsignedShortArrayList;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

import java.util.function.IntUnaryOperator;

/** An int list where most values are in range 0..2^16 */
final class MostlyUShortIntList implements IntUnaryOperator {
  private static final int IN_MAP = Character.MAX_VALUE;
  private final UnsignedShortArrayList myList;
  private Int2IntMap myMap;

  MostlyUShortIntList(int initialCapacity) {
    myList = new UnsignedShortArrayList(initialCapacity);
  }

  void add(int value) {
    if (value < 0 || value >= IN_MAP) {
      initMap().put(myList.size(), value);
      value = IN_MAP;
    }
    myList.add(value);
  }

  void set(int index, int value) {
    if (value < 0 || value >= IN_MAP) {
      initMap().put(index, value);
      value = IN_MAP;
    }
    myList.setQuick(index, value);
  }

  private Int2IntMap initMap() {
    if (myMap == null) {
      myMap = new Int2IntOpenHashMap();
    }
    return myMap;
  }

  @Override
  public int applyAsInt(int index) {
    return get(index);
  }

  public int get(int index) {
    int value = myList.getQuick(index);
    return value == IN_MAP ? myMap.get(index) : value;
  }

  int size() {
    return myList.size();
  }

  void trimToSize() {
    myList.trimToSize();
    if (myMap != null) {
      ((Int2IntOpenHashMap)myMap).trim();
    }
  }
}