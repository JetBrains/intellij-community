// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.fastutil.longs

import fleet.fastutil.longs.LongArrays.unwrap


// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.


fun LongSet.toLongArray(): LongArray {
  val size = size
  if (size == 0) return LongArrays.EMPTY_ARRAY
  val a = LongArray(size)
  unwrap(values, a)
  return a
}

fun <R> LongSet.mapNotNull(transform: (Long) -> R?): List<R> {
  val res = mutableListOf<R>()
  val iter = this.values
  while (iter.hasNext()) {
    val element = iter.next()
    val transformed = transform(element)
    if (transformed != null) {
      res.add(transformed)
    }
  }
  return res
}

fun <V> LongSet.map(transform: (Long) -> V): List<V> {
  val res = mutableListOf<V>()
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

inline fun LongSet.partition(predicate: (Long) -> Boolean): Pair<List<Long>, List<Long>> {
  val first = ArrayList<Long>()
  val second = ArrayList<Long>()
  this.values.forEach {
    if (predicate(it)) {
      first.add(it)
    } else {
      second.add(it)
    }
  }
  return Pair(first, second)
}