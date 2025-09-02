// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency;

import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;

public interface BaseMaplet<K> extends Closeable, Flushable {

  boolean containsKey(K key);

  void remove(K key);

  @NotNull
  Iterable<K> getKeys();

  @Override
  void close() throws IOException;

  @Override
  void flush() throws IOException;
}
