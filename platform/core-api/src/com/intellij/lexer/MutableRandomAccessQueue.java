// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lexer;

import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

class MutableRandomAccessQueue<T>  {
  private Object[] myArray;
  private int myFirst;
  private int myLast;
  // if true, elements are located at myFirst..myArray.length and 0..myLast
  // otherwise, they are at myFirst..myLast
  private boolean isWrapped;

  MutableRandomAccessQueue(int initialCapacity) {
    myArray = initialCapacity > 0 ? new Object[initialCapacity] : ArrayUtilRt.EMPTY_OBJECT_ARRAY;
  }

  void addLast(T object) {
    int currentSize = size();
    if (currentSize == myArray.length) {
      myArray = normalize(Math.max(currentSize * 3/2, 10));
      myFirst = 0;
      myLast = currentSize;
      isWrapped = false;
    }
    myArray[myLast] = object;
    myLast++;
    if (myLast == myArray.length) {
      isWrapped = !isWrapped;
      myLast = 0;
    }
  }

  void removeLast() {
    if (myLast == 0) {
      isWrapped = !isWrapped;
      myLast = myArray.length;
    }
    myLast--;
    myArray[myLast] = null;
  }

  private T getRaw(int last) {
    //noinspection unchecked
    return (T)myArray[last];
  }

  boolean isEmpty() {
    return size() == 0;
  }

  int size() {
    return isWrapped ? myArray.length - myFirst + myLast : myLast - myFirst;
  }

  T pullFirst() {
    T result = peekFirst();
    myArray[myFirst] = null;
    myFirst++;
    if (myFirst == myArray.length) {
      myFirst = 0;
      isWrapped = !isWrapped;
    }
    return result;
  }

  T peekFirst() {
    if (isEmpty()) {
      throw new IndexOutOfBoundsException("queue is empty");
    }
    return getRaw(myFirst);
  }

  private int copyFromTo(int first, int last, Object[] result, int destinationPos) {
    int length = last - first;
    System.arraycopy(myArray, first, result, destinationPos, length);
    return length;
  }

  private T @NotNull [] normalize(int capacity) {
    @SuppressWarnings("unchecked") T[] result = (T[])new Object[capacity];
    return normalize(result);
  }

  private T @NotNull [] normalize(T[] result) {
    if (isWrapped) {
      int tailLength = copyFromTo(myFirst, myArray.length, result, 0);
      copyFromTo(0, myLast, result, tailLength);
    }
    else {
      copyFromTo(myFirst, myLast, result, 0);
    }
    return result;
  }

  void clear() {
    Arrays.fill(myArray, null);
    myFirst = myLast = 0;
    isWrapped = false;
  }

  T set(int index, T value) {
    int arrayIndex = myFirst + index;
    if (isWrapped && arrayIndex >= myArray.length) {
      arrayIndex -= myArray.length;
    }
    T old = getRaw(arrayIndex);
    myArray[arrayIndex] = value;
    return old;
  }

  T get(int index) {
    int arrayIndex = myFirst + index;
    if (isWrapped && arrayIndex >= myArray.length) {
      arrayIndex -= myArray.length;
    }
    return getRaw(arrayIndex);
  }
}
