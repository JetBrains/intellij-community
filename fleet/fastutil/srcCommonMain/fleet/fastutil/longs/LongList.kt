// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.fastutil.longs

interface LongList {

  val size: Int

  /** Returns the element at the specified position in this list. */
  operator fun get(index: Int): Long

  /** Copies (hopefully quickly) elements of this type-specific list into the given array.
   *
   * @param from the start index (inclusive).
   * @param a the destination array. is an IntArray, so that we can use copyInto
   * @param offset the offset into the destination array where to store the first element copied.
   * @param length the number of elements to be copied.
   */
  fun toArray(from: Int, a: LongArray, offset: Int, length: Int): LongArray

  companion object {
    /** Returns an immutable empty list.
     *
     * @return an immutable empty list.
     */
    fun of(): LongList {
      return LongArrayList.of()
    }

    /** Returns an immutable list with the element given.
     *
     * @param e the element that the returned list will contain.
     * @return an immutable list containing `e`.
     */
    fun of(e: Long): LongList {
      return LongArrayList.of(e)
    }

    /** Returns an immutable list with the elements given.
     *
     * @param e0 the first element.
     * @param e1 the second element.
     * @return an immutable list containing `e0` and `e1`.
     */
    fun of(e0: Long, e1: Long): LongList {
      return LongArrayList.of(e0, e1)
    }

    /** Returns an immutable list with the elements given.
     *
     * @param e0 the first element.
     * @param e1 the second element.
     * @param e2 the third element.
     * @return an immutable list containing `e0`, `e1`, and `e2`.
     */
    fun of(e0: Long, e1: Long, e2: Long): LongList {
      return LongArrayList.of(e0, e1, e2)
    }

    /** Returns an immutable list with the elements given.
     *
     *
     * Note that this method does not perform a defensive copy.
     *
     * @param a a list of elements that will be used to initialize the immutable list.
     * @return an immutable list containing the elements of `a`.
     */
    fun of(vararg a: Long): LongList {
      when (a.size) {
        0 -> return of()
        1 -> return of(a[0])
        else -> {}
      }
      return LongArrayList.of(*a)
    }
  }
}