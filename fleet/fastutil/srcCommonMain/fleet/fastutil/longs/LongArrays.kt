// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.fastutil.longs

import kotlin.math.max
import kotlin.math.min

object LongArrays {
  val EMPTY_ARRAY: LongArray = longArrayOf()

  val DEFAULT_EMPTY_ARRAY: LongArray = longArrayOf()

  fun forceCapacity(array: LongArray, length: Int, preserve: Int): LongArray {
    val t = LongArray(length)
    array.copyInto(t, 0, 0, preserve)
    return t
  }

  fun ensureCapacity(array: LongArray, length: Int, preserve: Int): LongArray {
    return if (length > array.size) forceCapacity(array, length, preserve)
    else array
  }

  /** Grows the given array to the maximum between the given length and
   * the current length increased by 50%, provided that the given
   * length is larger than the current length.
   *
   *
   * If you want complete control on the array growth, you
   * should probably use `ensureCapacity()` instead.
   *
   * @param array an array.
   * @param length the new minimum length for this array.
   * @return `array`, if it can contain `length`
   * entries; otherwise, an array with
   * max(`length`,`array.length`/) entries whose first
   * `array.length` entries are the same as those of `array`.
   */
  fun grow(array: LongArray, length: Int): LongArray {
    return grow(array, length, array.size)
  }

  /** Grows the given array to the maximum between the given length and
   * the current length increased by 50%, provided that the given
   * length is larger than the current length, preserving just a part of the array.
   *
   * If you want complete control on the array growth, you
   * should probably use `ensureCapacity()` instead.
   *
   * @param array an array.
   * @param length the new minimum length for this array.
   * @param preserve the number of elements of the array that must be preserved in case a new allocation is necessary.
   * @return `array`, if it can contain `length`
   * entries; otherwise, an array with
   * max(`length`,`array.length`/) entries whose first
   * `preserve` entries are the same as those of `array`.
   */
  fun grow(array: LongArray, length: Int, preserve: Int): LongArray {
    if (length > array.size) {
      val newLength = max(min((array.size + (array.size shr 1)), fleet.fastutil.Arrays.MAX_ARRAY_SIZE), length)
      val t = LongArray(newLength)
      array.copyInto(t, destinationOffset = 0, startIndex = 0, endIndex = preserve)
      return t
    }
    return array
  }

  /** Trims the given array to the given length.
   *
   * @param array an array.
   * @param length the new maximum length for the array.
   * @return `array`, if it contains `length`
   * entries or less; otherwise, an array with
   * `length` entries whose entries are the same as
   * the first `length` entries of `array`.
   */
  fun trim(array: LongArray, length: Int): LongArray {
    if (length >= array.size) return array
    val t = if (length == 0) EMPTY_ARRAY else LongArray(length)
    array.copyInto(t, destinationOffset = 0, startIndex = 0, endIndex = length)
    return t
  }


  fun unwrap(i: MutableLongIterator): LongArray {
    return unwrap(i, Int.MAX_VALUE)
  }

  /** Unwraps an iterator into an array starting at a given offset for a given number of elements.
   *
   *
   * This method iterates over the given type-specific iterator and stores the elements
   * returned, up to a maximum of `length`, in the given array starting at `offset`.
   * The number of actually unwrapped elements is returned (it may be less than `max` if
   * the iterator emits less than `max` elements).
   *
   * @param i a type-specific iterator.
   * @param array an array to contain the output of the iterator.
   * @param offset the first element of the array to be returned.
   * @param max the maximum number of elements to unwrap.
   * @return the number of elements unwrapped.
   */
  fun unwrap(i: LongIterator, array: LongArray, offset: Int, max: Int): Int {
    var offset = offset
    if (max < 0) throw IllegalArgumentException("The maximum number of elements ($max) is negative")
    if (offset < 0 || offset + max > array.size) throw IllegalArgumentException()
    var j = max
    while (j-- != 0 && i.hasNext()) array[offset++] = i.next()
    return max - j - 1
  }

  /** Unwraps an iterator into an array.
   *
   *
   * This method iterates over the given type-specific iterator and stores the
   * elements returned in the given array. The iteration will stop when the
   * iterator has no more elements or when the end of the array has been reached.
   *
   * @param i a type-specific iterator.
   * @param array an array to contain the output of the iterator.
   * @return the number of elements unwrapped.
   */
  fun unwrap(i: LongIterator, array: LongArray): Int {
    return unwrap(i, array, 0, array.size)
  }

  /** Unwraps an iterator, returning an array, with a limit on the number of elements.
   *
   *
   * This method iterates over the given type-specific iterator and returns an array
   * containing the elements returned by the iterator. At most `max` elements
   * will be returned.
   *
   * @param i a type-specific iterator.
   * @param max the maximum number of elements to be unwrapped.
   * @return an array containing the elements returned by the iterator (at most `max`).
   */
  fun unwrap(i: LongIterator, max: Int): LongArray {
    var max = max
    if (max < 0) throw IllegalArgumentException("The maximum number of elements ($max) is negative")
    var array = LongArray(16)
    var j = 0
    while (max-- != 0 && i.hasNext()) {
      if (j == array.size) array = grow(array, j + 1)
      array[j++] = i.next()
    }
    return trim(array, j)
  }
}