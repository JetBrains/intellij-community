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
package com.intellij.openapi.vfs.impl;

import gnu.trove.PrimeFinder;

/**
 * IdentityHashMap that have the (immutable) keys stored in values (2 times more compact).
 */
final class AddonlyKeylessHash<K, V> {
  private int size;
  private Object[] entries;
  private final KeyValueMapper<K, V> keyValueMapper;

  AddonlyKeylessHash(KeyValueMapper<K, V> _keyValueMapper) {
    this(4, _keyValueMapper);
  }

  AddonlyKeylessHash(int expectedSize, KeyValueMapper<K, V> _keyValueMapper) {
    int i = PrimeFinder.nextPrime(5 * expectedSize / 4 + 1);
    entries = new Object[i];
    keyValueMapper = _keyValueMapper;
  }

  public int size() {
    return size;
  }

  public void add(V item) {
    if (size >= (4 * entries.length) / 5) rehash();
    V v = doPut(entries, item);
    if (v == null) size++;
  }

  private V doPut(Object[] a, V o) {
    K key = keyValueMapper.key(o);
    int index = hashIndex(a, key);
    V obj = (V)a[index];
    a[index] = o;
    return obj;
  }

  private int hashIndex(Object[] a, K key) {
    int hash = keyValueMapper.hash(key) & 0x7fffffff;
    int index = hash % a.length;
    V candidate = (V)a[index];
    if (candidate == null || keyValueMapper.valueHasKey(candidate, key)) {
      return index;
    }

    final int probe = 1 + (hash % (a.length - 2));

    do {
      index -= probe;
      if (index < 0) index += a.length;

      candidate = (V)a[index];
    }
    while (candidate != null && !keyValueMapper.valueHasKey(candidate, key));

    return index;
  }

  private void rehash() {
    Object[] b = new Object[PrimeFinder.nextPrime(entries.length * 2)];
    for (int i = entries.length; --i >= 0; ) {
      V ns = (V)entries[i];
      if (ns != null) doPut(b, ns);
    }
    entries = b;
  }

  public V get(K key) {
    return (V)entries[hashIndex(entries, key)];
  }

  public abstract static class KeyValueMapper<K, V> {
    public abstract int hash(K k);

    public abstract K key(V v);

    public boolean valueHasKey(V value, K key) {
      return key == key(value); // identity
    }

    protected boolean isIdentity() { return true; }
  }
}
