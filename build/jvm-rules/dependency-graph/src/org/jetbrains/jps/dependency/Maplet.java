// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency;

import org.jetbrains.annotations.Nullable;

public interface Maplet<K, V> extends BaseMaplet<K> {

  @Nullable
  V get(final K key);

  void put(K key, V value);
}
