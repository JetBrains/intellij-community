// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.fastutil.longs


interface MutableLongList: LongList {
  fun removeAt(index: Int): Long

  fun add(element: Long)

  fun add(index: Int, element: Long)

  /** Sets the size of this list.
   *
   *
   * If the specified size is smaller than the current size, the last elements are
   * discarded. Otherwise, they are filled with 0/`null`/`false`.
   *
   * @param size the new size.
   */
  fun resize(size: Int)

  operator fun set(index: Int, element: Long): Long

  /** Removes (hopefully quickly) elements of this type-specific list.
   *
   * @param from the start index (inclusive).
   * @param to the end index (exclusive).
   */
  fun removeElements(from: Int, to: Int)

  /** Add (hopefully quickly) elements to this type-specific list.
   *
   * @param index the index at which to add elements.
   * @param a the array containing the elements.
   * @param offset the offset of the first element to add.
   * @param length the number of elements to add.
   */
  fun addElements(index: Int, a: LongArray, offset: Int, length: Int)

  fun addAll(index: Int, elements: LongList): Boolean

  fun clear()

  fun sort()
}