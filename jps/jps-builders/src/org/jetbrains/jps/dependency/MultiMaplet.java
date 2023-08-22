// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency;

import org.jetbrains.annotations.Nullable;

public interface MultiMaplet<K, V> {
  boolean containsKey(final K key);

  @Nullable
  Iterable<V> get(final K key);

  void put(final K key, final Iterable<? extends V> values);

  void remove(final K key);


  void appendValue(final K key, final V value);

  default void appendValues(final K key, final Iterable<? extends V> values) {
    for (V value : values) {
      appendValue(key, value);
    }
  }

  void removeValue(final K key, final V value);

  default void removeValues(final K key, final Iterable<? extends V> values) {
    for (V value : values) {
      removeValue(key, value);
    }
  }

  Iterable<K> getKeys();
}
