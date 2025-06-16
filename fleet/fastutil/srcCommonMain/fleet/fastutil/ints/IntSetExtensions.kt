// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.fastutil.ints
import fleet.fastutil.ints.IntArrays.unwrap


fun IntSet.toIntArray(): IntArray {
  val size = size
  if (size == 0) return IntArrays.EMPTY_ARRAY
  val a = IntArray(size)
  unwrap(values, a)
  return a
}


fun <V> IntSet.map(transform: (Int) -> V): List<V> {
  val res = ArrayList<V>()
  val iter = this.values
  while (iter.hasNext()) {
    val element = iter.next()
    res.add(transform(element))
  }
  return res
}

fun <V> IntSet.mapNotNull(transform: (Int) -> V?): List<V> {
  val res = ArrayList<V>()
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

inline fun IntSet.partition(predicate: (Int) -> Boolean): Pair<List<Int>, List<Int>> {
  val first = ArrayList<Int>()
  val second = ArrayList<Int>()
  this.values.forEach {
    if (predicate(it)) {
      first.add(it)
    } else {
      second.add(it)
    }
  }
  return Pair(first, second)
}
