// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency;

import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.IOException;

public interface Maplet<K, V> extends Closeable {
  boolean containsKey(final K key);

  @Nullable
  V get(final K key);

  void put(K key, V value);

  void remove(K key);

  Iterable<K> getKeys();

  @Override
  void close() throws IOException;
}
