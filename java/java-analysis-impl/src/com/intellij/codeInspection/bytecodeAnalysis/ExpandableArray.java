// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.bytecodeAnalysis;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

final class ExpandableArray<T> {
  // Good trade-off between wasted allocation and amount of copying on our tests
  // Also we would not waste too much if the method complexity is close to 
  // Analysis.STEPS_LIMIT, which is currently 30000 (in this case we would allocate 30720 elements) 
  private static final int INITIAL_SIZE = 30;
  private T[] array;
  
  @Nullable
  @Contract(pure = true)
  T get(int index) {
    return array != null && index < array.length ? array[index] : null;
  }
  
  @SuppressWarnings("unchecked")
  void set(int index, T value) {
    if (array == null) {
      array = (T[])new Object[Math.max(index + 1, INITIAL_SIZE)];
    }
    else if (index >= array.length) {
      array = Arrays.copyOf(array, Math.max(index + 1, array.length * 2));
    }
    array[index] = value;
  }
}
