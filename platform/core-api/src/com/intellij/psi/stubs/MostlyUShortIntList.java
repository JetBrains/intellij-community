// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.stubs;

import com.intellij.util.IntIntFunction;
import com.intellij.util.containers.UnsignedShortArrayList;
import gnu.trove.TIntIntHashMap;

/** An int list where most values are in range 0..2^16 */
class MostlyUShortIntList implements IntIntFunction {
  private static final int IN_MAP = Character.MAX_VALUE;
  private final UnsignedShortArrayList myList;
  private TIntIntHashMap myMap;

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

  private TIntIntHashMap initMap() {
    if (myMap == null) myMap = new TIntIntHashMap();
    return myMap;
  }

  public int fun(int index) {
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
      myMap.trimToSize();
    }
  }
}