// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.memory;

import java.util.Arrays;
import java.util.function.IntConsumer;

class SortedIntSet implements Comparable<SortedIntSet> {
  private int[] myData;
  private int mySize;
  
  SortedIntSet() {
    mySize = 0;
    myData = new int[10];
  }

  SortedIntSet(int[] values) {
    myData = values.clone();
    Arrays.sort(myData);
    mySize = myData.length;
  }
  
  public int size() {
    return mySize;
  }
  
  public boolean isEmpty() {
    return mySize == 0;
  }

  public void add(int val) {
    int pos = indexOf(val);
    if (pos >= 0) return;
    if (mySize == myData.length) {
      myData = Arrays.copyOf(myData, mySize * 3 / 2 + 1);
    }
    System.arraycopy(myData, -pos - 1, myData, -pos, mySize + pos + 1);
    myData[-pos - 1] = val;
    mySize++;
  }

  public void add(int[] vals) {
    for (int val : vals) {
      add(val);
    }
  }
  
  public boolean contains(int val) {
    return indexOf(val) >= 0;
  }

  public void removeValue(int val) {
    int pos = indexOf(val);
    if (pos >= 0) {
      remove(pos);
    }
  }
  
  private int indexOf(int value) {
    for (int i = 0; i < mySize; i++) {
      int datum = myData[i];
      if (value == datum) return i;
      if (value < datum) return -i - 1;
    }
    return - mySize - 1;
  }
  
  public void remove(int offset) {
    System.arraycopy(myData, offset + 1, myData, offset, mySize - offset - 1);
    mySize--;
  }

  public boolean containsAll(SortedIntSet that) {
    int thatSize = that.size();
    int thisSize = this.size();
    if (thatSize > thisSize) return false;
    if (thatSize == thisSize) {
      return Arrays.equals(myData, 0, thisSize, that.myData, 0, thatSize);
    }
    int thisIndex=0;
    for (int thatIndex = 0; thatIndex < thatSize; thatIndex++) {
      int thatValue = that.myData[thatIndex];
      while (thisIndex < thisSize && this.myData[thisIndex] < thatValue) {
        thisIndex++;
      }
      if (thisIndex == thisSize || this.myData[thisIndex] > thatValue) return false;
      thisIndex++;
    }
    return true;
  }

  @Override
  public int compareTo(SortedIntSet t) {
    if (t == this) return 0;
    if (t.size() != size()) return Integer.compare(size(), t.size());
    return Arrays.compare(myData, 0, mySize, t.myData, 0, mySize);
  }

  public int get(int pos) {
    if (pos >= mySize) throw new IllegalArgumentException();
    return myData[pos];
  }

  public void forValues(IntConsumer consumer) {
    for (int i = 0; i < mySize; i++) {
      consumer.accept(myData[i]);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SortedIntSet set = (SortedIntSet)o;
    if (mySize != set.mySize) return false;
    return Arrays.equals(myData, 0, mySize, set.myData, 0, mySize);
  }

  @Override
  public int hashCode() {
    int size = mySize;
    int[] arr = myData;
    int result = size;
    for (int i = 0; i < size; i++) {
      int element = arr[i];
      result = 31 * result + element;
    }
    return result;
  }

  protected int[] toNativeArray() {
    return Arrays.copyOf(myData, mySize);
  }
}
