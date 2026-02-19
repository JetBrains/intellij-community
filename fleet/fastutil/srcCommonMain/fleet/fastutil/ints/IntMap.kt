// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.fastutil.ints

interface IntMap<V> {
  val size: Int
  val keys: IntIterator
  val values: Iterator<V>
  val entries: Iterator<IntEntry<V>>

  operator fun get(key: Int): V?
}

