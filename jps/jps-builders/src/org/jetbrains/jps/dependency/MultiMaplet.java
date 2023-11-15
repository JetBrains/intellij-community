// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency;

import org.jetbrains.annotations.NotNull;

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

}
