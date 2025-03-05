// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.fastutil.longs

import fleet.fastutil.Hash
import fleet.fastutil.HashCommon
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min


/** A type-specific hash map with a fast, small-footprint implementation.
 *
 *
 * Instances of this class use a hash table to represent a map. The table is
 * filled up to a specified *load factor*, and then doubled in size to
 * accommodate new entries. If the table is emptied below *one fourth*
 * of the load factor, it is halved in size; however, the table is never reduced to a
 * size smaller than that at creation time: this approach makes it
 * possible to create maps with a large capacity in which insertions and
 * deletions do not cause immediately rehashing. Moreover, halving is
 * not performed when deleting entries from an iterator, as it would interfere
 * with the iteration process.
 *
 *
 * Note that [.clear] does not modify the hash table size.
 * Rather, a family of [trimming][.trim] lets you control the size of the table; this is particularly useful
 * if you reuse instances of this class.
 *
 *
 * @see Hash
 *
 * @see HashCommon
 */

class Long2ObjectOpenHashMap<V> : Hash, MutableLongMap<V> {
  /** The array of keys.  */
  private var key: LongArray

  /** The array of values.  */
  private var value: Array<V?>

  /** The mask for wrapping a position counter.  */
  private var mask: Int

  /** Whether this map contains the key zero.  */
  private var containsNullKey: Boolean = false

  /** The current table size.  */
  private var n: Int

  /** Threshold after which we rehash. It must be the table size times [.f].  */
  private var maxFill: Int

  /** We never resize below this threshold, which is the construction-time {#n}.  */
  private var minN: Int

  /** Number of entries in the set (including the key zero, if present).  */
  override var size: Int = 0

  /** The acceptable load factor.  */
  private var f: Float

  private var defaultValue: V? = null

  /** Iterator over entries.  */
  override val entries: Iterator<LongEntry<V>>
    get() {
      return EntryIterator()
    }

  /** Iterator over keys*/
  override val keys: LongIterator
    get() {
      return KeyIterator()
    }

  /** Iterator over values.  */
  override val values: Iterator<V>
    get() {
      return ValueIterator()
    }

  constructor(expected: Int, f: Float) {
    if (f <= 0 || f >= 1) throw IllegalArgumentException("Load factor must be greater than 0 and smaller than 1")
    if (expected < 0) throw IllegalArgumentException("The expected number of elements must be nonnegative")
    this.f = f
    n = HashCommon.arraySize(expected, f)
    minN = n
    mask = n - 1
    maxFill = HashCommon.maxFill(n, f)
    key = LongArray(n + 1)
    value = arrayOfNulls<Any>(n + 1) as Array<V?>
  }

  constructor() : this(Hash.DEFAULT_INITIAL_SIZE, Hash.DEFAULT_LOAD_FACTOR)

  constructor(expected: Int) : this(expected, Hash.DEFAULT_LOAD_FACTOR)

  /** Creates a new hash map with [Hash.DEFAULT_LOAD_FACTOR] as load factor copying a given one.
   *
   * @param m a [Map] to be copied into the new hash map.
   */
  constructor(m: Long2ObjectOpenHashMap<V>, f: Float = Hash.DEFAULT_LOAD_FACTOR) : this(m.size, f) {
    putAll(m)
  }

  /** Creates a new hash map with [Hash.DEFAULT_LOAD_FACTOR] as load factor using the elements of two parallel arrays.
   *
   * @param k the array of keys of the new hash map.
   * @param v the array of corresponding values in the new hash map.
   * @throws IllegalArgumentException if `k` and `v` have different lengths.
   */
  constructor(k: LongArrayList, v: Array<V>, f: Float = Hash.DEFAULT_LOAD_FACTOR) : this(k.size, f) {
    if (k.size != v.size) throw IllegalArgumentException("The key array and the value array have different lengths (" + k.size + " and " + v.size + ")")
    for (i in k.indices) this[k[i]] = v[i]
  }

  constructor(k: LongArray, v: Array<V>, f: Float = Hash.DEFAULT_LOAD_FACTOR) : this(k.size, f) {
    if (k.size != v.size) throw IllegalArgumentException("The key array and the value array have different lengths (" + k.size + " and " + v.size + ")")
    for (i in k.indices) this[k[i]] = v[i]
  }

  fun isEmpty(): Boolean {
    return size == 0
  }

  private fun realSize(): Int {
    return if (containsNullKey) size - 1 else size
  }

  /** Ensures that this map can hold a certain number of keys without rehashing.
   *
   * @param capacity a number of keys; there will be no rehashing unless
   * the map [size][.size] exceeds this number.
   */
  private fun ensureCapacity(capacity: Int) {
    val needed: Int = HashCommon.arraySize(capacity, f)
    if (needed > n) rehash(needed)
  }

  private fun tryCapacity(capacity: Long) {
    val needed = min(1 shl 30, max(2, HashCommon.nextPowerOfTwo(ceil(capacity / f).toInt())))
    if (needed > n) rehash(needed)
  }

  private fun removeEntry(pos: Int): V? {
    val oldValue = value[pos]
    value[pos] = null
    size--
    shiftKeys(pos)
    if (n > minN && size < maxFill / 4 && n > Hash.DEFAULT_INITIAL_SIZE) rehash(n / 2)
    return oldValue
  }

  private fun removeNullEntry(): V {
    containsNullKey = false
    val oldValue = value[n]
    value[n] = null
    size--
    if (n > minN && size < maxFill / 4 && n > Hash.DEFAULT_INITIAL_SIZE) rehash(n / 2)
    return oldValue!!
  }


  fun putAll(from: Long2ObjectOpenHashMap<V>) {
    if (f <= .5) ensureCapacity(from.size) // The resulting map will be sized for m.size() elements
    else tryCapacity((size + from.size).toLong()) // The resulting map will be tentatively sized for size() + m.size() elements

    var n = from.size
    val i = from.entries.iterator()
    var e: LongEntry<V>
    while (n-- != 0) {
      e = i.next()
      put(e.key, e.value)
    }
  }

  private fun find(k: Long): Int {
    if (k == 0L) return if (containsNullKey) n else -(n + 1)
    var curr: Long
    val key = this.key
    var pos: Int // The starting point.
    if (key[(HashCommon.mix(k).toInt() and mask).also {
        pos = it
      }].also { curr = it } == 0L) return -(pos + 1)
    if (k == curr) return pos // There's always an unused entry.
    while (true) {
      if (key[((pos + 1) and mask).also { pos = it }].also {
          curr = it
        } == 0L) return -(pos + 1)
      if (k == curr) return pos
    }
  }

  private fun insert(pos: Int, k: Long, v: V) {
    if (pos == n) containsNullKey = true
    key[pos] = k
    value[pos] = v
    if (size++ >= maxFill) rehash(HashCommon.arraySize(size + 1, f))
  }

  override fun put(key: Long, value: V): V? {
    val pos = find(key)
    if (pos < 0) {
      insert(-pos - 1, key, value)
      return defaultReturnValue()
    }
    val oldValue = this.value[pos]
    this.value[pos] = value
    this.key[pos] = key
    return oldValue
  }

  /** Shifts left entries with the specified hash code, starting at the specified position,
   * and empties the resulting free entry.
   *
   * @param pos a starting position.
   */
  private fun shiftKeys(pos: Int) { // Shift entries with the same hash.
    var pos = pos
    var last: Int
    var slot: Int
    var curr: Long
    val key = this.key
    val value: Array<V?> = this.value
    while (true) {
      pos = (pos.also { last = it } + 1) and mask
      while (true) {
        if (key[pos].also { curr = it } == 0L) {
          key[last] = 0
          value[last] = null
          return
        }
        slot = HashCommon.mix(curr).toInt() and mask
        if (if (last <= pos) last >= slot || slot > pos else slot in (pos + 1)..last) break
        pos = (pos + 1) and mask
      }
      key[last] = curr
      value[last] = value[pos]
    }
  }

  override fun remove(k: Long): V? {
    if (k == 0L) {
      if (containsNullKey) return removeNullEntry()
      return defaultReturnValue()
    }
    var curr: Long
    val key = this.key
    var pos: Int // The starting point.
    if (key[(HashCommon.mix(k).toInt() and mask).also {
        pos = it
      }].also { curr = it } == 0L) return defaultReturnValue()
    if (k == curr) return removeEntry(pos)
    while (true) {
      if (key[((pos + 1) and mask).also { pos = it }].also {
          curr = it
        } == 0L) return defaultReturnValue()
      if (k == curr) return removeEntry(pos)
    }
  }

  override operator fun get(k: Long): V? {
    if (k == 0L) return if (containsNullKey) value[n] else defaultReturnValue()
    var curr: Long
    val key = this.key
    var pos: Int // The starting point.
    if (key[(HashCommon.mix(k).toInt() and mask).also {
        pos = it
      }].also { curr = it } == 0L) return defaultReturnValue()
    if (k == curr) return value[pos] // There's always an unused entry.
    while (true) {
      if (key[((pos + 1) and mask).also { pos = it }].also {
          curr = it
        } == 0L) return defaultReturnValue()
      if (k == curr) return value[pos]
    }
  }

  fun containsKey(k: Long): Boolean {
    if (k == 0L) return containsNullKey
    var curr: Long
    val key = this.key
    var pos: Int // The starting point.
    if (key[(HashCommon.mix(k).toInt() and mask).also {
        pos = it
      }].also { curr = it } == 0L) return false
    if (k == curr) return true // There's always an unused entry.
    while (true) {
      if (key[((pos + 1) and mask).also { pos = it }].also {
          curr = it
        } == 0L) return false
      if (k == curr) return true
    }
  }

  fun containsValue(v: V): Boolean {
    if (containsNullKey && value[n] == v) return true
    var i = n
    while (i-- != 0) {
      if (key[i] != 0L && value[i] == v) return true
    }
    return false
  }


  fun getOrDefault(k: Long, defaultValue: V): V {
    if (k == 0L) return if (containsNullKey) value[n]!! else defaultValue
    var curr: Long
    val key = this.key
    var pos: Int // The starting point.
    if (key[HashCommon.mix(k).toInt() and mask].also { pos = it.toInt() }.also { curr = it } == 0L) return defaultValue
    if (k == curr) return value[pos]!! // There's always an unused entry.
    while (true) {
      if (key[(pos + 1) and mask].also { pos = it.toInt() }.also { curr = it } == 0L) return defaultValue
      if (k == curr) return value[pos]!!
    }
  }

  fun remove(k: Long, v: Any): Boolean {
    if (k == 0L) {
      if (containsNullKey && v == value[n]) {
        removeNullEntry()
        return true
      }
      return false
    }
    var curr: Long
    val key = this.key
    var pos: Int // The starting point.
    if (key[HashCommon.mix(k).toInt() and mask].also { pos = it.toInt() }.also { curr = it } == 0L) return false
    if (k == curr && v == value[pos]) {
      removeEntry(pos)
      return true
    }
    while (true) {
      if (key[(pos + 1) and mask].also { pos = it.toInt() }.also { curr = it } == 0L) return false
      if (k == curr && v == value[pos]) {
        removeEntry(pos)
        return true
      }
    }
  }

  fun defaultReturnValue(): V? {
    return defaultValue
  }

  fun defaultReturnValue(rv: V) {
    defaultValue = rv
  }

  fun replace(k: Long, oldValue: V, v: V): Boolean {
    val pos = find(k)
    if (pos < 0 || oldValue != value[pos]) return false
    value[pos] = v
    return true
  }

  fun replace(k: Long, v: V): V {
    val pos = find(k)
    if (pos < 0) return defaultReturnValue()!!
    val oldValue = value[pos]
    value[pos] = v
    return oldValue!!
  }


  /** Removes all elements from this map.
   *
   * <p>To increase object reuse, this method does not change the table size.
   * If you want to reduce the table size, you must use {@link #trim()}.
   *
   */
  fun clear() {
    if (size == 0) return
    size = 0
    containsNullKey = false
    key.fill(0)
    value.fill(null)
  }

  /** An iterator over a hash map.  */
  private abstract inner class MapIterator {
    /** The index of the last entry returned, if positive or zero; initially, [.n]. If negative, the last
     * entry returned was that of the key of index `- pos - 1` from the [.wrapped] list.  */
    var pos: Int = n

    /** The index of the last entry that has been returned (more precisely, the value of [.pos] if [.pos] is positive,
     * or [Int.MIN_VALUE] if [.pos] is negative). It is -1 if either
     * we did not return an entry yet, or the last returned entry has been removed.  */
    var last: Int = -1

    /** A downward counter measuring how many entries must still be returned.  */
    var c: Int = size

    /** A boolean telling us whether we should return the entry with the null key.  */
    var mustReturnNullKey: Boolean = this@Long2ObjectOpenHashMap.containsNullKey

    /** A lazily allocated list containing keys of entries that have wrapped around the table because of removals.  */
    var wrapped: LongArrayList? = null


    fun hasNext(): Boolean {
      return c != 0
    }

    fun nextEntry(): Int {
      if (!hasNext()) throw NoSuchElementException()
      c--
      if (mustReturnNullKey) {
        mustReturnNullKey = false
        return n.also { last = it }
      }
      val key = this@Long2ObjectOpenHashMap.key
      while (true) {
        if (--pos < 0) { // We are just enumerating elements from the wrapped list.
          last = Int.MIN_VALUE
          if (wrapped == null) throw IllegalStateException()
          val k: Long = wrapped!!.get(-pos - 1)
          var p: Int = HashCommon.mix(k).toInt() and mask
          while (k != key[p]) p = (p + 1) and mask
          return p
        }
        if (key[pos] != 0L) return pos.also { last = it }
      }
    }

    /** Shifts left entries with the specified hash code, starting at the specified position,
     * and empties the resulting free entry.
     *
     * @param pos a starting position.
     */
    private fun shiftKeys(pos: Int) { // Shift entries with the same hash.
      var pos = pos
      var last: Int
      var slot: Int
      var curr: Long
      val key = this@Long2ObjectOpenHashMap.key
      val value: Array<V?> = this@Long2ObjectOpenHashMap.value
      while (true) {
        pos = (pos.also { last = it } + 1) and mask
        while (true) {
          if ((key[pos].also { curr = it }) == 0L) {
            key[last] = (0)
            value[last] = null
            return
          }
          slot = HashCommon.mix(curr).toInt() and mask
          if (if (last <= pos) last >= slot || slot > pos else slot in (pos + 1)..last) break
          pos = (pos + 1) and mask
        }
        if (pos < last) { // Wrapped entry.
          if (wrapped == null) wrapped = LongArrayList(2)
          wrapped!!.add(key[pos])
        }
        key[last] = curr
        value[last] = value[pos]
      }
    }
  }

  // Iterator on entries
  private inner class EntryIterator : MapIterator(), Iterator<LongEntry<V>> {
    override fun next(): LongEntry<V> {
      val nextIndex = nextEntry()
      return LongEntry(key[nextIndex], value[nextIndex]!!)
    }
  }

  // Iterator on keys
  private inner class KeyIterator : MapIterator(), LongIterator {
    override fun next(): Long {
      return key[nextEntry()]
    }
  }

  // An iterator on values.
  private inner class ValueIterator : MapIterator(), Iterator<V> {
    override fun next(): V {
      return value[nextEntry()]!!
    }
  }

  /** Rehashes the map, making the table as small as possible.
   *
   *
   * This method rehashes the table to the smallest size satisfying the
   * load factor. It can be used when the set will not be changed anymore, so
   * to optimize access speed and size.
   *
   *
   * If the table size is already the minimum possible, this method
   * does nothing.
   *
   * @return true if there was enough memory to trim the map.
   * @see .trim
   */
  fun trim(n: Int = size): Boolean {
    val l: Int = HashCommon.nextPowerOfTwo(ceil((n / f)).toInt())
    if (l >= this.n || size > HashCommon.maxFill(l, f)) return true
    try {
      rehash(l)
    }
    catch (cantDoIt: Exception) {
      return false
    }
    return true
  }

  /** Rehashes the map.
   *
   *
   * This method implements the basic rehashing strategy, and may be
   * overridden by subclasses implementing different rehashing strategies (e.g.,
   * disk-based rehashing). However, you should not override this method
   * unless you understand the internal workings of this class.
   *
   * @param newN the new size
   */
  private fun rehash(newN: Int) {
    val key = this.key
    val value = this.value
    val mask = newN - 1 // Note that this is used by the hashing macro
    val newKey = LongArray(newN + 1)
    val newValue = arrayOfNulls<Any>(newN + 1) as Array<V?>
    var i = n
    var pos: Int
    var j = realSize()
    while (j-- != 0) {
      while (key[--i] == 0L);
      if (newKey[(HashCommon.mix(key[i]).toInt() and mask).also { pos = it }] != 0L) while (newKey[(pos + 1 and mask).also { pos = it }] != 0L);
      newKey[pos] = key[i]
      newValue[pos] = value[i]
    }
    newValue[newN] = value[n]
    n = newN
    this.mask = mask
    maxFill = HashCommon.maxFill(n, f)
    this.key = newKey
    this.value = newValue
  }

  override fun equals(o: Any?): Boolean {
    if (o === this) return true
    if (o !is LongMap<*>) return false

    if (this.size != o.size) return false

    for ((key, value) in this.entries) {
      if (o[key] != value) return false
    }
    return true
  }

  /** Returns a hash code for this map.
   *
   * This method overrides the generic method provided by the superclass.
   * Since `equals()` is not overriden, it is important
   * that the value returned by this method is the same value as
   * the one returned by the overriden method.
   *
   * @return a hash code for this map.
   */
  override fun hashCode(): Int {
    var h = 0
    val key = this.key
    val value: Array<V?> = this.value
    var j = realSize()
    var i = 0
    var t: Int
    while (j-- != 0) {
      while (key[i] == 0L) i++
      t = key[i].toInt()
      if (this !== value[i]) t = t xor (if (value[i] == null) 0 else value[i].hashCode())
      h += t
      i++
    } // Zero / null keys have hash zero.
    if (containsNullKey) h += if (value[n] == null) 0 else value[n].hashCode()
    return h
  }
}