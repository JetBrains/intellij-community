// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.fastutil.longs


interface MutableLongMap<V>: LongMap<V> {
  fun put(key: Long, value: V): V?
  fun remove(key: Long): V?

  operator fun set(key: Long, value: V): V? {
    return put(key, value)
  }
}