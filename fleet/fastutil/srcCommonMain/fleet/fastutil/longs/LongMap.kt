// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.fastutil.longs


interface LongMap<V> {
  val size: Int
  val keys: LongIterator
  val values: Iterator<V>
  val entries: Iterator<LongEntry<V>>

  operator fun get(key: Long): V?
}