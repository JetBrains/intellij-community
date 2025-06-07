// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.fastutil.ints

import fleet.fastutil.Arrays

inline fun MutableIntList.removeIf(predicate: (Int) -> Boolean): Boolean {
  var removed = false
  var curIndex = indices.first
  while (curIndex in indices) {
    if (predicate(get(curIndex))) {
      removeAt(curIndex)
      removed = true
    } else {
      curIndex += 1
    }
  }
  return removed
}

fun MutableIntList.retainAll(elements: IntList): Boolean {
  // Convert to Set for better runtime
  val elementsSet = IntOpenHashSet(elements)
  return removeIf{ elem -> !elementsSet.contains(elem) }
}

fun MutableIntList.removeAll(elements: IntList): Boolean {
  var modified = false
  for (index in elements.indices) {
    if (removeValue(elements[index])) {
      modified = true
    }
  }
  return modified
}

/** Set (hopefully quickly) elements to match the array given.
 *
 * @param index the index at which to start setting elements.
 * @param a the array containing the elements
 * @param offset the offset of the first element to add.
 * @param length the number of elements to add.
 * @since 8.3.0
 */
fun MutableIntList.setElements(index: Int, a: IntList, offset: Int, length: Int) { // We can't use AbstractList#ensureIndex, sadly.
  if (index < 0) throw IndexOutOfBoundsException("Index ($index) is negative")
  if (index > size) throw IndexOutOfBoundsException("Index ($index) is greater than list size ($size)")
  Arrays.ensureOffsetLength(a, offset, length)
  if (index + length > size) throw IndexOutOfBoundsException("End index (" + (index + length) + ") is greater than list size (" + size + ")")

  var iter = index
  var i = 0
  while (i < length) {
    this.set(iter, a[offset + i++])
    iter++
  }
}

/** Set (hopefully quickly) elements to match the array given.
 * @param a the array containing the elements.
 * @since 8.3.0
 */
fun MutableIntList.setElements(a: IntList) {
  setElements(0, a)
}

/** Set (hopefully quickly) elements to match the array given.
 * @param index the index at which to start setting elements.
 * @param a the array containing the elements.
 * @since 8.3.0
 */
fun MutableIntList.setElements(index: Int, a: IntList) {
  setElements(index, a, 0, a.size)
}

fun MutableIntList.addAll(elements: IntList): Boolean {
  return addAll(size, elements)
}

fun MutableIntList.addElements(index: Int, a: IntArray) {
  addElements(index, a, 0, a.size)
}


fun MutableIntList.pop(): Int {
  if (isEmpty()) throw IndexOutOfBoundsException()
  return removeAt(size - 1)
}

fun MutableIntList.removeValue(key: Int): Boolean {
    val index = indexOf(key)
    if (index == -1) return false
    removeAt(index)
    return true
}