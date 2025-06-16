// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.fastutil.ints

interface MutableIntMap<V>: IntMap<V> {
  fun put(key: Int, value: V): V?
  fun remove(key: Int): V?

  operator fun set(key: Int, value: V): V? {
    return put(key, value)
  }
}