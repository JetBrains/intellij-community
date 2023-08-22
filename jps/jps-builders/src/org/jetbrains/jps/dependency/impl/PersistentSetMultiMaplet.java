// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.dependency.MultiMaplet;
import org.jetbrains.jps.dependency.NodeSerializerRegistry;
import org.jetbrains.jps.dependency.SerializableGraphElement;

public class PersistentSetMultiMaplet<K extends SerializableGraphElement, V extends SerializableGraphElement> implements MultiMaplet<K, V> {

  private final NodeSerializerRegistry mySerializerRegistry;
  // todo: provide implementation with persistent map


  public PersistentSetMultiMaplet(@NotNull NodeSerializerRegistry serializerRegistry) {
    mySerializerRegistry = serializerRegistry;
  }

  @Override
  public boolean containsKey(K key) {
    return false;
  }

  @Override
  public @Nullable Iterable<V> get(K key) {
    return null;
  }

  @Override
  public void put(K key, Iterable<? extends V> values) {

  }

  @Override
  public void remove(K key) {

  }

  @Override
  public void appendValue(K key, V value) {

  }

  @Override
  public void removeValue(K key, V value) {

  }

  @Override
  public Iterable<K> getKeys() {
    return null;
  }
}
