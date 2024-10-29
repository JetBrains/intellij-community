// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeStyle.arrangement.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

@SuppressWarnings("unchecked")
public final class IntObjectMap<V> {
  
  private Object @NotNull [] myData;
  private int myMaxUsed = -1;
  private int mySize;

  public IntObjectMap() {
    this(16);
  }

  public IntObjectMap(int initialCapacity) {
    myData = new Object[initialCapacity];
  }

  public @Nullable V get(int key) {
    if (key < 0 || key >= myData.length) {
      return null;
    }
    return (V)myData[key];
  }

  public void set(int key, @NotNull V value) {
    if (key >= myData.length) {
      int newCapacity = myData.length;
      while (newCapacity <= key) {
        newCapacity <<= 1;
      }
      myData = Arrays.copyOf(myData, newCapacity);
    }
    if (myData[key] == null) {
      mySize++;
    }
    myData[key] = value;
    myMaxUsed = Math.max(myMaxUsed, key);
  }

  public void remove(int key) {
    if (key < 0 || key >= myData.length) {
      return;
    }
    if (myData[key] != null) {
      mySize--;
    }
    myData[key] = null;
    if (key == myMaxUsed) {
      for (int i = myMaxUsed; i >= 0; i--) {
        if (myData[i] != null) {
          myMaxUsed = i;
          return;
        }
      }
      myMaxUsed = -1;
    }
    
  }

  public void clear() {
    myData = new Object[myData.length];
    myMaxUsed = -1;
    mySize = 0;
  }
  
  public void shiftKeys(final int from, int shift) {
    if (shift == 0 || from > myMaxUsed) {
      return;
    }
    
    int newEnd = myMaxUsed + shift;
    if (newEnd >= myData.length) {
      int minCapacity = newEnd + 1;
      int newCapacity = myData.length;
      while (newCapacity < minCapacity) {
        newCapacity <<= 1;
      }
      myData = Arrays.copyOf(myData, newCapacity);
    }
    
    int effectiveFrom = from;
    while (myData[effectiveFrom] == null ) {
      if (++effectiveFrom >= myData.length) {
        return;
      }
    }
    System.arraycopy(myData, effectiveFrom, myData, effectiveFrom + shift, myMaxUsed - effectiveFrom + 1);

    if (shift > 0) {
      for (int i = effectiveFrom, max = effectiveFrom + shift; i < max; i++) {
        myData[i] = null;
      }
    }
    else {
      for (int i = myMaxUsed, min = myMaxUsed + shift; i > min; i--) {
        myData[i] = null;
      }
    }

    myMaxUsed += shift;
  }
  
  public int size() {
    return mySize;
  }
}
