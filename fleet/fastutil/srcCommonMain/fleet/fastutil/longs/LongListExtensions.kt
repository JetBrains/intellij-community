// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.fastutil.longs

fun LongList.containsAll(elements: Collection<Long>): Boolean {
  for (element in elements) if (!contains(element)) return false
  return true
}

fun LongList.ensureIndex(index: Int) {
  if (index < 0) throw IndexOutOfBoundsException("Index ($index) is negative")
  if (index > size) throw IndexOutOfBoundsException("Index ($index) is greater than list size ($size)")
}

inline fun LongList.forEach(action: (Long) -> Unit) {for(index in indices) action(get(index))}

fun LongList.contains(element: Long): Boolean {
  return indexOf(element) != -1
}

fun LongList.isEmpty(): Boolean {
  return size == 0
}

fun LongList.isNotEmpty(): Boolean {
  return size != 0
}

fun LongList.sum(): Long {
  var sum = 0L
  for (i in indices) sum += get(i)
  return sum
}

val LongList.indices: IntRange
  get() = 0 ..<size

fun LongList.length(): Int {
  return size
}

fun LongList.toArray(): LongArray {
  val size = size
  if (size == 0) return LongArrays.EMPTY_ARRAY
  val ret = LongArray(size)
  toArray(0, ret, 0, size)
  return ret
}

fun LongList.indexOf(element: Long): Int {
  for(i in 0 until size) {
    if (element == get(i)) return i
  }
  return -1

}

fun LongList.toArray(from: Int, to: Int, a: LongArray): LongArray {
  @Suppress("NAME_SHADOWING") var a = a
  if (a.size < size) a = a.copyOf(size)
  toArray(from, a, 0, to)
  return a
}


fun LongList.lastIndexOf(element: Long): Int {
  var i = size
  while (i-- != 0) {
    if (element == get(i)) return i
  }
  return -1
}

fun <R> LongList.firstNotNullOfOrNull(transform: (Long) -> R?): R? {
  for (index in this.indices) {
    val result = transform(this[index])
    if (result != null) {
      return result
    }
  }
  return null
}