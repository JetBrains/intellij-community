/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.editor.impl;

import com.intellij.util.ArrayFactory;
import com.intellij.util.ArrayUtil;
import com.intellij.util.concurrency.AtomicFieldUpdater;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

/**
 * Maintains an atomic immutable array of listeners of type {@code T} in sorted order according to {@link #comparator}
 * N.B. internal array is exposed for faster iterating listeners in to- and reverse order, so care should be taken for not mutating it by clients
 */
class LockFreeCOWSortedArray<T> {
  @NotNull private final Comparator<? super T> comparator;
  private final ArrayFactory<T> arrayFactory;
  /** changed by {@link #UPDATER} only */
  @SuppressWarnings("FieldMayBeFinal")
  @NotNull private volatile T[] listeners;
  private static final AtomicFieldUpdater<LockFreeCOWSortedArray, Object[]> UPDATER = AtomicFieldUpdater.forFieldOfType(LockFreeCOWSortedArray.class, Object[].class);

  LockFreeCOWSortedArray(@NotNull Comparator<? super T> comparator, @NotNull ArrayFactory<T> arrayFactory) {
    this.comparator = comparator;
    this.arrayFactory = arrayFactory;
    listeners = arrayFactory.create(0);
  }

  // returns true if changed
  void add(@NotNull T listener) {
    while (true) {
      T[] oldListeners = listeners;
      int i = insertionIndex(oldListeners, listener);
      T[] newListeners = ArrayUtil.insert(oldListeners, i, listener);
      if (UPDATER.compareAndSet(this, oldListeners, newListeners)) break;
    }
  }

  boolean remove(@NotNull T listener) {
    while (true) {
      T[] oldListeners = listeners;
      T[] newListeners = ArrayUtil.remove(oldListeners, listener, arrayFactory);
      //noinspection ArrayEquality
      if (oldListeners == newListeners) return false;
      if (UPDATER.compareAndSet(this, oldListeners, newListeners)) break;
    }
    return true;
  }

  private int insertionIndex(@NotNull T[] elements, @NotNull T e) {
    for (int i=0; i<elements.length; i++) {
      T element = elements[i];
      if (comparator.compare(e, element) < 0) {
        return i;
      }
    }
    return elements.length;
  }

  @NotNull
  T[] getArray() {
    return listeners;
  }
}
