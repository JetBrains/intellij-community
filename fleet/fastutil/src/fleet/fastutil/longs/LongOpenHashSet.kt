/*
	* Copyright (C) 2002-2024 Sebastiano Vigna
	*
	* Licensed under the Apache License, Version 2.0 (the "License");
	* you may not use this file except in compliance with the License.
	* You may obtain a copy of the License at
	*
	*     http://www.apache.org/licenses/LICENSE-2.0
	*
	* Unless required by applicable law or agreed to in writing, software
	* distributed under the License is distributed on an "AS IS" BASIS,
	* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	* See the License for the specific language governing permissions and
	* limitations under the License.
	*/
package fleet.fastutil.longs

import fleet.fastutil.Arrays
import fleet.fastutil.Hash
import fleet.fastutil.HashCommon
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**  A type-specific hash set with with a fast, small-footprint implementation.
 *
 *
 * Instances of this class use a hash table to represent a set. The table is
 * filled up to a specified *load factor*, and then doubled in size to
 * accommodate new entries. If the table is emptied below *one fourth*
 * of the load factor, it is halved in size; however, the table is never reduced to a
 * size smaller than that at creation time: this approach makes it
 * possible to create sets with a large capacity in which insertions and
 * deletions do not cause immediately rehashing. Moreover, halving is
 * not performed when deleting entries from an iterator, as it would interfere
 * with the iteration process.
 *
 *
 * Note that [.clear] does not modify the hash table size.
 * Rather, a family of [trimming][.trim] lets you control the size of the table; this is particularly useful
 * if you reuse instances of this class.
 *
 * @see Hash
 *
 * @see HashCommon
 */
class LongOpenHashSet(
  expected: Int = Hash.DEFAULT_INITIAL_SIZE,
  f: Float = Hash.DEFAULT_LOAD_FACTOR,
): MutableLongSet{
  /** The array of keys.  */
  private var key: LongArray

  /** The mask for wrapping a position counter.  */
  private var mask = 0

  /** Whether this set contains the null key.  */
  private var containsNull = false

  /** The current table size. Note that an additional element is allocated for storing the null key.  */
  private var n = 0

  /** Threshold after which we rehash. It must be the table size times [.f].  */
  private var maxFill = 0

  /** We never resize below this threshold, which is the construction-time {#n}.  */
  private val minN: Int

  /** Number of entries in the set (including the null key, if present).  */
  override var size = 0

  /** The acceptable load factor.  */
  private val f: Float

  override val values: MutableLongIterator
    get() = SetIterator()

  /** Creates a new hash set.
   * The actual table size will be the least power of two greater than `expected`/`f`.
   */
  init {
    if (f <= 0 || f >= 1) throw IllegalArgumentException("Load factor must be greater than 0 and smaller than 1")
    if (expected < 0) throw IllegalArgumentException("The expected number of elements must be nonnegative")
    this.f = f
    n = HashCommon.arraySize(expected, f)
    minN = n
    mask = n - 1
    maxFill = HashCommon.maxFill(n, f)
    key = LongArray(n + 1)
  }

  /** Creates a new hash set copying a given collection.
   *
   * @param c a [LongList] to be copied into the new hash set.
   * @param f the load factor.
   */
  constructor(c: LongList, f: Float = Hash.DEFAULT_LOAD_FACTOR) : this(c.size, f) {
    addAll(c)
  }

  constructor(c: Collection<Long>, f:Float = Hash.DEFAULT_LOAD_FACTOR) : this(c.size, f) {
    addAll(c)
  }

  /** Creates a new hash set using elements provided by a type-specific iterator.
   *
   * @param i a type-specific iterator whose elements will fill the set.
   * @param f the load factor.
   */
  constructor(
    i: LongIterator,
    f: Float = Hash.DEFAULT_LOAD_FACTOR,
  ) : this(Hash.DEFAULT_INITIAL_SIZE, f) {
    while (i.hasNext()) add(i.next())
  }

  /** Creates a new hash set with [Hash.DEFAULT_LOAD_FACTOR] as load factor and fills it with the elements of a given array.
   *
   * @param a an array whose elements will be used to fill the set.
   * @param offset the first element to use.
   * @param length the number of elements to use.
   * @param f the load factor
   */
  constructor(
    a: LongList,
    offset: Int,
    length: Int,
    f: Float = Hash.DEFAULT_LOAD_FACTOR,
  ) : this(if (length < 0) 0 else length, f) {
    Arrays.ensureOffsetLength(a, offset, length)
    for (i in 0 until length) add(a[offset + i])
  }

  private fun realSize(): Int {
    return if (containsNull) size - 1 else size
  }

  /** Ensures that this set can hold a certain number of elements without rehashing.
   *
   * @param capacity a number of elements; there will be no rehashing unless
   * the set [size][.size] exceeds this number.
   */
  private fun ensureCapacity(capacity: Int) {
    val needed: Int = HashCommon.arraySize(capacity, f)
    if (needed > n) rehash(needed)
  }

  private fun tryCapacity(capacity: Long) {
    val needed = min(1 shl 30, max(2, HashCommon.nextPowerOfTwo(ceil(capacity / f).toLong()).toInt()))
    if (needed > n) rehash(needed)
  }

  fun addAll(elements: Collection<Long>): Boolean {
    if (f <= .5) ensureCapacity(elements.size) // The resulting collection will be sized for c.size() elements
    else tryCapacity((size + elements.size).toLong()) // The resulting collection will be tentatively sized for size() + c.size() elements
    var retVal = false

    val i = elements.iterator()
    while (i.hasNext()) {
      if (add(i.next())) retVal = true
    }
    return retVal
  }

  fun addAll(elements: LongList): Boolean {
    if (f <= .5) ensureCapacity(elements.size) // The resulting collection will be sized for c.size() elements
    else tryCapacity((size + elements.size).toLong()) // The resulting collection will be tentatively sized for size() + c.size() elements

    var modified = false
    for (index in elements.indices) {
      if (add(elements[index])) modified = true
    }
    return modified
  }

  override fun add(element: Long): Boolean {
    var pos: Int
    if (element == 0L) {
      if (containsNull) return false
      containsNull = true
    }
    else {
      var curr: Long
      val key = this.key // The starting point.
      if (key[(HashCommon.mix(element).toInt() and mask).also {
          pos = it
        }].also { curr = it } != 0L) {
        if (curr == element) return false
        while (key[((pos + 1) and mask).also { pos = it }].also {
            curr = it
          } != 0L) if (curr == element) return false
      }
      key[pos] = element
    }

    if (size++ >= maxFill) rehash(HashCommon.arraySize(size + 1, f))
    return true
  }

  /** Shifts left entries with the specified hash code, starting at the specified position,
   * and empties the resulting free entry.
   *
   * @param pos a starting position.
   */
  private fun shiftKeys(pos: Int) { // Shift entries with the same hash.
    @Suppress("NAME_SHADOWING") var pos = pos
    var last: Int
    var slot: Int
    var curr: Long
    val key = this.key
    while (true) {
      pos = ((pos.also { last = it }) + 1) and mask
      while (true) {
        if ((key[pos].also { curr = it }) == 0L) {
          key[last] = 0
          return
        }
        slot = HashCommon.mix(curr).toInt() and mask
        if (if (last <= pos) last >= slot || slot > pos else slot in (pos + 1)..last) break
        pos = (pos + 1) and mask
      }
      key[last] = curr
    }
  }

  private fun removeEntry(pos: Int): Boolean {
    size--
    shiftKeys(pos)
    if (n > minN && size < maxFill / 4 && n > Hash.DEFAULT_INITIAL_SIZE) rehash(n / 2)
    return true
  }

  private fun removeNullEntry(): Boolean {
    containsNull = false
    key[n] = 0
    size--
    if (n > minN && size < maxFill / 4 && n > Hash.DEFAULT_INITIAL_SIZE) rehash(n / 2)
    return true
  }

  override fun remove(element: Long): Boolean {
    if (element == 0L) {
      if (containsNull) return removeNullEntry()
      return false
    }
    var curr: Long
    val key = this.key
    var pos: Int // The starting point.
    if (key[(HashCommon.mix(element).toInt() and mask).also {
        pos = it
      }].also { curr = it } == 0L) return false
    if (element == curr) return removeEntry(pos)
    while (true) {
      if (key[(pos + 1 and mask).also { pos = it }].also {
          curr = it
        } == 0L) return false
      if (element == curr) return removeEntry(pos)
    }
  }

  override fun contains(element: Long): Boolean {
    if (element == 0L) return containsNull
    var curr: Long
    val key = this.key
    var pos: Int // The starting point.
    if (key[(HashCommon.mix(element).toInt() and mask).also {
        pos = it
      }].also { curr = it } == 0L) return false
    if (element == curr) return true
    while (true) {
      if (key[(pos + 1 and mask).also { pos = it }].also {
          curr = it
        } == 0L) return false
      if (element == curr) return true
    }
  }

  /* Removes all elements from this set.
 * To increase object reuse, this method does not change the table size.
 * If you want to reduce the table size, you must use trim().
 */
  fun clear() {
    if (size == 0) return
    size = 0
    containsNull = false
    key.fill(0)
  }

  /** An iterator over a hash set.  */
  private inner class SetIterator : MutableLongIterator {
    /** The index of the last entry returned, if positive or zero; initially, [.n]. If negative, the last
     * element returned was that of index `- pos - 1` from the [.wrapped] list.  */
    var pos = n

    /** The index of the last entry that has been returned (more precisely, the value of [.pos] if [.pos] is positive,
     * or [Int.MIN_VALUE] if [.pos] is negative). It is -1 if either
     * we did not return an entry yet, or the last returned entry has been removed.  */
    var last = -1

    /** A downward counter measuring how many entries must still be returned.  */
    var c: Int = size

    /** A boolean telling us whether we should return the null key.  */
    var mustReturnNull: Boolean = this@LongOpenHashSet.containsNull

    /** A lazily allocated list containing elements that have wrapped around the table because of removals.  */
    lateinit var wrapped: LongArrayList

    override fun hasNext(): Boolean {
      return c != 0
    }

    override fun next(): Long {
      if (!hasNext()) throw NoSuchElementException()
      c--
      val key = this@LongOpenHashSet.key
      if (mustReturnNull) {
        mustReturnNull = false
        last = n
        return key[n]
      }
      while (true) {
        if (--pos < 0) { // We are just enumerating elements from the wrapped list.
          last = Int.MIN_VALUE
          return wrapped.get(-pos - 1)
        }
        if (key[pos] != 0L) return key[pos.also {
          last = it
        }]
      }

    }

    override fun remove() {
      if (last == -1) throw IllegalStateException()
      if (last == n) {
        this@LongOpenHashSet.containsNull = false
        key[n] = 0
      }
      else if (pos >= 0) shiftKeys(last)
      else { // We're removing wrapped entries.
        this@LongOpenHashSet.remove(wrapped.get(-pos - 1))
        last = -1 // Note that we must not decrement size
        return
      }
      size--
      last = -1 // You can no longer remove this entry.
    }

    /** Shifts left entries with the specified hash code, starting at the specified position,
     * and empties the resulting free entry.
     *
     * @param pos a starting position.
     */
    private fun shiftKeys(pos: Int) { // Shift entries with the same hash.
      @Suppress("NAME_SHADOWING") var pos = pos
      var last: Int
      var slot: Long
      var curr: Long
      val key = this@LongOpenHashSet.key
      while (true) {
        pos = (pos.also { last = it } + 1) and mask
        while (true) {
          if (key[pos].also { curr = it } == 0L) {
            key[last] = 0
            return
          }
          slot = HashCommon.mix(curr) and mask.toLong()
          if (if (last <= pos) last >= slot || slot > pos else slot in (pos + 1)..last) break
          pos = (pos + 1) and mask
        }
        if (pos < last) { // Wrapped entry.
          if (!::wrapped.isInitialized) wrapped = LongArrayList(2)
          wrapped.add(key[pos])
        }
        key[last] = curr
      }
    }
  }

  /**
   * This method rehashes the table to the smallest size satisfying the
   * load factor. It can be used when the set will not be changed anymore, so
   * to optimize access speed and size.
   *
   *
   * If the table size is already the minimum possible, this method
   * does nothing.
   *
   * @return true if there was enough memory to trim the set.
   * @see .trim
   */
  fun trim(n: Int = size): Boolean {
    val l = HashCommon.nextPowerOfTwo(ceil(n / f).toInt())
    if (l >= this.n || size > HashCommon.maxFill(l, f)) return true
    try {
      rehash(l)
    }
    catch (cantDoIt: Throwable) {
      return false
    }
    return true
  }

  /** Rehashes the set.
   *
   * This method implements the basic rehashing strategy, and may be
   * overriden by subclasses implementing different rehashing strategies (e.g.,
   * disk-based rehashing). However, you should not override this method
   * unless you understand the internal workings of this class.
   *
   * @param newN the new size
   */
  private fun rehash(newN: Int) {
    val key = this.key
    val mask = newN - 1 // Note that this is used by the hashing macro
    val newKey = LongArray(newN + 1)
    var i = n
    var pos: Int
    var j = realSize()
    while (j-- != 0) {
      while (key[--i] == 0L);
      if (newKey[(HashCommon.mix(key[i]).toInt() and mask).also {
          pos = it
        }] != 0L) while (newKey[((pos + 1) and mask).also {
          pos = it
        }] != 0L);
      newKey[pos] = key[i]
    }
    n = newN
    this.mask = mask
    maxFill = HashCommon.maxFill(n, f)
    this.key = newKey
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (super.equals(other)) return true
    if (other is LongSet) {
      if (other.size != this.size) return false
      return containsAll(other)
    }
    return false
  }




  /** Returns a hash code for this set.
   *
   * This method overrides the generic method provided by the superclass.
   * Since `equals()` is not overriden, it is important
   * that the value returned by this method is the same value as
   * the one returned by the overriden method.
   *
   * @return a hash code for this set.
   */
  override fun hashCode(): Int {
    var h = 0
    val key = this@LongOpenHashSet.key
    var j = realSize()
    var i = 0
    while (j-- != 0) {
      while (key[i] == 0L) i++
      h += key[i].toInt()
      i++
    } // Zero / null have hash zero.
    return h
  }

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