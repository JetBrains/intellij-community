// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.util.ArrayFactory;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.VarHandleWrapper;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

/**
 * Maintains an atomic immutable array of listeners of type {@code T} in sorted order according to {@link #comparator}
 * N.B. internal array is exposed for faster iterating listeners in to- and reverse order, so care should be taken for not mutating it by clients
 */
class LockFreeCOWSortedArray<T> {
  private final @NotNull Comparator<? super T> comparator;
  private final @NotNull ArrayFactory<? extends T> arrayFactory;
  @SuppressWarnings("FieldMayBeFinal") private volatile T @NotNull [] array;
  private static final VarHandleWrapper ARRAY_HANDLE = VarHandleWrapper.getFactory().create(LockFreeCOWSortedArray.class, "array", Object[].class);

  LockFreeCOWSortedArray(@NotNull Comparator<? super T> comparator, @NotNull ArrayFactory<? extends T> arrayFactory) {
    this.comparator = comparator;
    this.arrayFactory = arrayFactory;
    array = arrayFactory.create(0);
  }

  // returns true if changed
  void add(@NotNull T element) {
    while (true) {
      T[] oldArray = getArray();
      int i = insertionIndex(oldArray, element);
      T[] newArray = ArrayUtil.insert(oldArray, i, element);
      if (ARRAY_HANDLE.compareAndSet(this, oldArray, newArray)) {
        break;
      }
    }
  }

  boolean remove(@NotNull T listener) {
    while (true) {
      T[] oldArray = getArray();
      T[] newArray = ArrayUtil.remove(oldArray, listener, arrayFactory);
      //noinspection ArrayEquality
      if (oldArray == newArray) return false;
      if (ARRAY_HANDLE.compareAndSet(this, oldArray, newArray)) {
        break;
      }
    }
    return true;
  }

  private int insertionIndex(T @NotNull [] elements, @NotNull T e) {
    for (int i=0; i<elements.length; i++) {
      T element = elements[i];
      if (comparator.compare(e, element) < 0) {
        return i;
      }
    }
    return elements.length;
  }

  T @NotNull [] getArray() {
    return array;
  }
}
