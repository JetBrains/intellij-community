package fleet.fastutil.ints

import kotlin.math.max
import kotlin.math.min

object IntArrays {
  val EMPTY_ARRAY: IntArray = intArrayOf()

  val DEFAULT_EMPTY_ARRAY: IntArray = intArrayOf()

  fun forceCapacity(array: IntArray, length: Int, preserve: Int): IntArray {
    val t = IntArray(length)
    array.copyInto(t, 0, 0, preserve)
    return t
  }

  fun ensureCapacity(array: IntArray, length: Int, preserve: Int): IntArray {
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
  fun grow(array: IntArray, length: Int): IntArray {
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
  fun grow(array: IntArray, length: Int, preserve: Int): IntArray {
    if (length > array.size) {
      val newLength = max(min((array.size + (array.size shr 1)), fleet.fastutil.Arrays.MAX_ARRAY_SIZE), length)
      val t = IntArray(newLength)
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
  fun trim(array: IntArray, length: Int): IntArray {
    if (length >= array.size) return array
    val t = if (length == 0) EMPTY_ARRAY else IntArray(length)
    array.copyInto(t, destinationOffset = 0, startIndex = 0, endIndex = length)
    return t
  }


  fun unwrap(i: IntIterator): IntArray {
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
  fun unwrap(i: IntIterator, array: IntArray, offset: Int, max: Int): Int {
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
  fun unwrap(i: IntIterator, array: IntArray): Int {
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
  fun unwrap(i: IntIterator, max: Int): IntArray {
    var max = max
    if (max < 0) throw IllegalArgumentException("The maximum number of elements ($max) is negative")
    var array = IntArray(16)
    var j = 0
    while (max-- != 0 && i.hasNext()) {
      if (j == array.size) array = grow(array, j + 1)
      array[j++] = i.next()
    }
    return trim(array, j)
  }
}