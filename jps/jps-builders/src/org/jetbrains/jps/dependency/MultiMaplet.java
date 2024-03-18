// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.diff.Difference;
import org.jetbrains.jps.javac.Iterators;

import java.util.function.BiFunction;

public interface MultiMaplet<K, V> extends BaseMaplet<K> {

  @NotNull
  Iterable<V> get(K key);

  void put(K key, @NotNull Iterable<? extends V> values);

  void appendValue(K key, final V value);

  default void appendValues(K key, @NotNull Iterable<? extends V> values) {
    for (V value : values) {
      appendValue(key, value);
    }
  }

  void removeValue(K key, V value);

  default void removeValues(K key, @NotNull Iterable<? extends V> values) {
    for (V value : values) {
      removeValue(key, value);
    }
  }

  default void update(K key, @NotNull Iterable<V> dataAfter, BiFunction<? super Iterable<V>, ? super Iterable<V>, Difference.Specifier<? extends V, ?>> diffComparator) {
    Iterable<V> dataBefore = get(key);
    boolean beforeEmpty = Iterators.isEmpty(dataBefore);
    boolean afterEmpty = Iterators.isEmpty(dataAfter);
    if (beforeEmpty || afterEmpty) {
      if (!afterEmpty) { // => beforeEmpty
        appendValues(key, dataAfter);
      }
      else if (!beforeEmpty) {
        remove(key);
      }
    }
    else {
      var diff = diffComparator.apply(dataBefore, dataAfter);
      if (!diff.unchanged()) {
        if (Iterators.isEmpty(diff.removed()) && Iterators.isEmpty(diff.changed())) {
          appendValues(key, diff.added());
        }
        else {
          put(key, dataAfter);
        }
      }
    }
  }

}
