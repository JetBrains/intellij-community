// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.fastutil.ints
import fleet.util.fastutil.ints.IntArrays.unwrap
import fleet.util.fastutil.longs.LongSet


fun IntSet.toIntArray(): IntArray {
  val size = size
  if (size == 0) return IntArrays.EMPTY_ARRAY
  val a = IntArray(size)
  unwrap(values, a)
  return a
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

fun IntSet.mapNotNull(transform: (Int) -> Int?): IntArrayList {
  val res = IntArrayList()
  val iter = this.values
  while (iter.hasNext()) {
    val element = iter.next()
    if (transform(element) != null) {
      res.add(element)
    }
  }
  return res
}

fun IntSet.isEmpty(): Boolean {
  return size == 0
}

fun IntSet.isNotEmpty(): Boolean {
  return size != 0
}

inline fun IntSet.forEach(transform: (Int) -> Unit) {
  val iter = values
  while (iter.hasNext()) {
    transform(iter.next())
  }
}

fun IntSet.containsAll(other: IntSet): Boolean {
  val iter = other.values
  while(iter.hasNext()) {
    if (!contains(iter.next())) return false
  }
  return true
}