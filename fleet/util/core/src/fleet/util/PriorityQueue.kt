// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util


class PriorityQueue<T>(private val comparator: Comparator<T>) : MutableCollection<T> {
  private val heap = ArrayList<T>()


  override fun contains(element: T): Boolean {
    return heap.contains(element)
  }

  override fun containsAll(elements: Collection<T>): Boolean {
    return heap.containsAll(elements)
  }

  override val size: Int
    get() = heap.size

  override fun isEmpty() = heap.isEmpty()

  override fun add(element: T): Boolean {
    heap.add(element)
    siftUp(size - 1, element)
    return true
  }

  override fun addAll(elements: Collection<T>): Boolean {
    for (element: T in elements) {
      add(element)
    }
    return true
  }

  fun indexOf(elem: T): Int {
    return heap.indexOf(elem)
  }

  override fun remove(element: T): Boolean {
    val index = indexOf(element)
    if (index == -1) {
      return false
    }
    removeAt(index)
    return true
  }

  private fun removeAt(index: Int) {
    if (index !in indices) {
      throw IndexOutOfBoundsException()
    }

    val s = size - 1
    if (s == index) {
      heap.removeLast()
      return
    }
    val moved = heap[s]
    heap.removeLast()
    siftDown(index, moved)
    if (heap[index] == moved) {
      siftUp(index, moved)
      if (heap[index] != moved) {
        return
      }
    }
    return

  }

  override fun clear() {
    heap.clear()
  }

  private fun siftUp(index: Int, elem: T) {
    var i = index
    while (i > 0) {
      val parent = (i - 1) / 2
      val parentElem = heap[parent]
      if (comparator.compare(elem, parentElem) >= 0) break
      heap[i] = parentElem
      i = parent
    }
    heap[i] = elem
  }


  private fun siftDown(index: Int, elem: T) {
    var k = index
    val half: Int = size / 2

    while (k < half) {
      var left = 2 * k + 1
      var oldElem = heap[left]
      val right = left + 1
      if (right < size && comparator.compare(oldElem, heap[right]) > 0) {
        oldElem = heap[right]
        left = right
      }
      if (comparator.compare(elem, oldElem) <= 0) break

      heap[k] = oldElem
      k = left
    }
    heap[k] = elem
  }

  fun poll(): T? {
    if (isEmpty()) {
      return null
    }
    val res = peek()
    removeAt(0)
    return res
  }

  fun peek(): T? {
    if (isEmpty()) {
      return null
    }
    return heap[0]
  }

  fun removeIf(pred: (T) -> Boolean): Boolean {
    var removedElement = false
    val toBeRemoved = arrayListOf<T>()
    for (elem in heap) {
      if (pred(elem)) {
        removedElement = true
        toBeRemoved.add(elem)
      }
    }
    for (elem in toBeRemoved) {
      remove(elem)
    }
    return removedElement
  }

  override fun iterator(): MutableIterator<T> {
    return heap.iterator()
  }

  override fun retainAll(elements: Collection<T>): Boolean {
    return removeIf { elem: T -> !elements.contains(elem) }
  }

  override fun removeAll(elements: Collection<T>): Boolean {
    return removeIf { elem: T -> elements.contains(elem) }
  }
}

fun <T : Comparable<T>> PriorityQueue(): PriorityQueue<T> {
  return PriorityQueue(naturalOrder())
}