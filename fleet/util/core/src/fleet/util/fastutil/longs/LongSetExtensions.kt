// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.fastutil.longs

import fleet.util.fastutil.ints.IntOpenHashSet
import fleet.util.fastutil.longs.LongArrays.unwrap


// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.


fun LongSet.toLongArray(): LongArray {
  val size = size
  if (size == 0) return LongArrays.EMPTY_ARRAY
  val a = LongArray(size)
  unwrap(values, a)
  return a
}

fun LongSet.mapNotNull(transform: (Long) -> Long?): LongArrayList {
  val res = LongArrayList()
  val iter = this.values
  while (iter.hasNext()) {
    val element = iter.next()
    if (transform(element) != null) {
      res.add(element)
    }
  }
  return res
}

fun <V> LongSet.map(transform: (Long) -> V): Set<V> {
  val res = HashSet<V>()
  val iter = this.values
  while (iter.hasNext()) {
    val element = iter.next()
    res.add(transform(element))
  }
  return res
}

fun LongSet.isEmpty(): Boolean {
  return size == 0
}

fun LongSet.isNotEmpty(): Boolean {
  return size != 0
}

inline fun LongSet.forEach(transform: (Long) -> Unit) {
  val iter = values
  while (iter.hasNext()) {
    transform(iter.next())
  }
}

fun LongSet.containsAll(other: LongSet): Boolean {
  val iter = other.values
  while(iter.hasNext()) {
    if (!contains(iter.next())) return false
  }
  return true
}