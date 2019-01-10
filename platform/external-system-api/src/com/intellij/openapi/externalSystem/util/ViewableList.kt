// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.util

import java.util.*

/**
 * Viewable list for simple implementation are list views
 * Unsafe for concurrent modification
 */
abstract class ViewableList<T> : MutableList<T> {
  abstract override val size: Int

  abstract override fun add(index: Int, element: T)

  abstract override fun set(index: Int, element: T): T

  abstract override fun removeAt(index: Int): T

  abstract override fun get(index: Int): T

  override fun contains(element: T): Boolean {
    for (i in 0 until size) {
      if (element == get(i)) {
        return true
      }
    }
    return false
  }

  override fun containsAll(elements: Collection<T>): Boolean {
    return elements.all(::contains)
  }

  override fun indexOf(element: T): Int {
    for (i in 0 until size) {
      if (element == get(i)) {
        return i
      }
    }
    return -1
  }

  override fun isEmpty(): Boolean {
    return size == 0
  }

  override fun iterator(): MutableIterator<T> {
    var index = 0
    return object : MutableIterator<T> {
      override fun hasNext(): Boolean {
        return index < size
      }

      override fun next(): T {
        return get(index++)
      }

      override fun remove() {
        removeAt(index--)
      }
    }
  }

  override fun lastIndexOf(element: T): Int {
    for (i in (0 until size).reversed()) {
      if (element == get(i)) {
        return i
      }
    }
    return -1
  }

  override fun add(element: T): Boolean {
    add(size, element)
    return true
  }

  override fun addAll(index: Int, elements: Collection<T>): Boolean {
    for (i in 0 until elements.size) {
      add(index + i, elements.elementAt(i))
    }
    return true
  }

  override fun addAll(elements: Collection<T>): Boolean {
    for (element in elements) {
      add(element)
    }
    return true
  }

  override fun clear() {
    for (i in (0 until size).reversed()) {
      removeAt(i)
    }
  }

  override fun listIterator(): MutableListIterator<T> {
    return listIterator(0)
  }

  override fun listIterator(index: Int): MutableListIterator<T> {
    var currentIndex = index
    return object : MutableListIterator<T> {
      override fun hasPrevious(): Boolean {
        return currentIndex > 0
      }

      override fun nextIndex(): Int {
        return currentIndex
      }

      override fun previous(): T {
        return get(--currentIndex)
      }

      override fun previousIndex(): Int {
        return currentIndex - 1
      }

      override fun add(element: T) {
        add(currentIndex++, element)
      }

      override fun hasNext(): Boolean {
        return currentIndex < size
      }

      override fun next(): T {
        return get(currentIndex++)
      }

      override fun remove() {
        removeAt(currentIndex--)
      }

      override fun set(element: T) {
        set(currentIndex - 1, element)
      }
    }
  }

  override fun remove(element: T): Boolean {
    val index = indexOf(element)
    if (index == -1) return false
    removeAt(index)
    return true
  }

  override fun removeAll(elements: Collection<T>): Boolean {
    var modified = false
    for (i in 0 until size) {
      if (elements.contains(get(i))) {
        removeAt(i)
        modified = true
      }
    }
    return modified
  }

  override fun retainAll(elements: Collection<T>): Boolean {
    var modified = false
    for (i in 0 until size) {
      if (!elements.contains(get(i))) {
        removeAt(i)
        modified = true
      }
    }
    return modified
  }

  override fun subList(fromIndex: Int, toIndex: Int): MutableList<T> {
    var currentSize = toIndex - fromIndex
    return object : ViewableList<T>() {
      override val size: Int
        get() = currentSize

      override fun add(index: Int, element: T) {
        validate(index)
        ++currentSize
        this@ViewableList.add(index + fromIndex, element)
      }

      override fun set(index: Int, element: T): T {
        validate(index)
        return this@ViewableList.set(index + fromIndex, element)
      }

      override fun removeAt(index: Int): T {
        validate(index)
        --currentSize
        return this@ViewableList.removeAt(index + fromIndex)
      }

      override fun get(index: Int): T {
        validate(index)
        return this@ViewableList[index + fromIndex]
      }

      private fun validate(index: Int) {
        if (index in currentSize until size) {
          throw IllegalArgumentException()
        }
      }
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is List<*>) return false
    if (size != other.size) return false

    for (i in 0 until size) {
      if (!Objects.equals(get(i), other[i])) {
        return false
      }
    }
    return true
  }

  override fun hashCode(): Int {
    var result = 0
    for (i in 0 until size) {
      result = 31 * result + get(i).hashCode()
    }
    return result
  }

  override fun toString(): String {
    val joiner = StringJoiner(", ")
    for (i in 0 until size) {
      joiner.add(get(i).toString())
    }
    return "[$joiner]"
  }
}