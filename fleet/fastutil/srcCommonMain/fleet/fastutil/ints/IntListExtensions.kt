// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.fastutil.ints

fun IntList.containsAll(elements: Collection<Int>): Boolean {
  for (element in elements) if (!contains(element)) return false
  return true
}

fun IntList.ensureIndex(index: Int) {
  if (index < 0) throw IndexOutOfBoundsException("Index ($index) is negative")
  if (index > size) throw IndexOutOfBoundsException("Index ($index) is greater than list size ($size)")
}

inline fun IntList.forEach(action: (Int) -> Unit) {for(index in indices) action(get(index))}

fun IntList.contains(element: Int): Boolean {
  return indexOf(element) != -1
}

fun IntList.isEmpty(): Boolean {
  return size == 0
}

fun IntList.isNotEmpty(): Boolean {
  return size != 0
}

fun IntList.sum(): Int {
  var sum = 0
  for (i in indices) sum += get(i)
  return sum
}

val IntList.indices: IntRange
  get() = 0 ..<size

fun IntList.length(): Int {
  return size
}

fun IntList.toArray(): IntArray {
  val size = size
  if (size == 0) return IntArrays.EMPTY_ARRAY
  val ret = IntArray(size)
  toArray(0, ret, 0, size)
  return ret
}

fun IntList.indexOf(element: Int): Int {
  for(i in 0 until size) {
    if (element == get(i)) return i
  }
  return -1

}

fun IntList.toArray(from: Int, to: Int, a: IntArray): IntArray {
  @Suppress("NAME_SHADOWING") var a = a
  if (a.size < size) a = a.copyOf(size)
  toArray(from, a, 0, to)
  return a
}


fun IntList.lastIndexOf(element: Int): Int {
  var i = size
  while (i-- != 0) {
    if (element == get(i)) return i
  }
  return -1
}

fun <R> IntList.firstNotNullOfOrNull(transform: (Int) -> R?): R? {
  for (index in this.indices) {
    val result = transform(this[index])
    if (result != null) {
      return result
    }
  }
  return null
}
