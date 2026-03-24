// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.impl;

import org.jetbrains.jps.dependency.ComparableTypeExternalizer;
import org.jetbrains.jps.dependency.Maplet;
import org.jetbrains.jps.dependency.MapletFactory;
import org.jetbrains.jps.dependency.MultiMaplet;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MemoryMapletFactory implements MapletFactory, Closeable {
  private final List<Closeable> myCreatedMaps = new ArrayList<>();

  @Override
  public <K, V> MultiMaplet<K, V> createSetMultiMaplet(String storageName, ComparableTypeExternalizer<K> keyExternalizer, ComparableTypeExternalizer<V> valueExternalizer) {
    MemoryMultiMaplet<K, V, Set<V>> map = new MemoryMultiMaplet<>(HashSet::new);
    myCreatedMaps.add(map);
    return map;
  }

  @Override
  public <K, V> Maplet<K, V> createMaplet(String storageName, ComparableTypeExternalizer<K> keyExternalizer, ComparableTypeExternalizer<V> valueExternalizer) {
    MemoryMaplet<K, V> map = new MemoryMaplet<>();
    myCreatedMaps.add(map);
    return map;
  }

  @Override
  public void close() throws IOException {
    try {
      IOException ex = null;
      for (Closeable closeable : myCreatedMaps) {
        try {
          closeable.close();
        }
        catch (IOException e) {
          ex = e;
        }
      }
      if (ex != null) {
        throw ex;
      }
    }
    finally {
      myCreatedMaps.clear();
    }
  }
}
