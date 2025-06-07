// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.fastutil.longs

import fleet.fastutil.Hash

interface LongSet {
  val size: Int
  val values: LongIterator
  operator fun contains(value: Long): Boolean

  companion object {

    /** Creates a new empty hash set.
     *
     * @return a new empty hash set.
     */
    fun of(): LongOpenHashSet {
      return LongOpenHashSet()
    }

    /** Creates a new hash set with [Hash.DEFAULT_LOAD_FACTOR] as load factor
     * using the given element.
     *
     * @param e the element that the returned set will contain.
     * @return a new hash set with [Hash.DEFAULT_LOAD_FACTOR] as load factor containing `e`.
     */
    fun of(e: Long): LongOpenHashSet {
      val result = LongOpenHashSet(1, Hash.DEFAULT_LOAD_FACTOR)
      result.add(e)
      return result
    }

    /** Creates a new hash set with [Hash.DEFAULT_LOAD_FACTOR] as load factor
     * using the elements given.
     *
     * @param e0 the first element.
     * @param e1 the second element.
     * @return a new hash set with [Hash.DEFAULT_LOAD_FACTOR] as load factor containing `e0` and `e1`.
     * @throws IllegalArgumentException if there were duplicate entries.
     */
    fun of(e0: Long, e1: Long): LongOpenHashSet {
      val result = LongOpenHashSet(2, Hash.DEFAULT_LOAD_FACTOR)
      result.add(e0)
      if (!result.add(e1)) {
        throw IllegalArgumentException("Duplicate element: $e1")
      }
      return result
    }

    /** Creates a new hash set with [Hash.DEFAULT_LOAD_FACTOR] as load factor
     * using the elements given.
     *
     * @param e0 the first element.
     * @param e1 the second element.
     * @param e2 the third element.
     * @return a new hash set with [Hash.DEFAULT_LOAD_FACTOR] as load factor containing `e0`, `e1`, and `e2`.
     * @throws IllegalArgumentException if there were duplicate entries.
     */
    fun of(e0: Long, e1: Long, e2: Long): LongOpenHashSet {
      val result = LongOpenHashSet(3, Hash.DEFAULT_LOAD_FACTOR)
      result.add(e0)
      if (!result.add(e1)) {
        throw IllegalArgumentException("Duplicate element: $e1")
      }
      if (!result.add(e2)) {
        throw IllegalArgumentException("Duplicate element: $e2")
      }
      return result
    }

    /** Creates a new hash set with [Hash.DEFAULT_LOAD_FACTOR] as load factor
     * using a list of elements.
     *
     * @param a a list of elements that will be used to initialize the new hash set.
     * @return a new hash set with [Hash.DEFAULT_LOAD_FACTOR] as load factor containing the elements of `a`.
     * @throws IllegalArgumentException if a duplicate entry was encountered.
     */
    fun of(vararg a: Long): LongOpenHashSet {
      val result = LongOpenHashSet(a.size, Hash.DEFAULT_LOAD_FACTOR)
      for (element in a) {
        if (!result.add(element)) {
          throw IllegalArgumentException("Duplicate element $element")
        }
      }
      return result
    }
  }
}
