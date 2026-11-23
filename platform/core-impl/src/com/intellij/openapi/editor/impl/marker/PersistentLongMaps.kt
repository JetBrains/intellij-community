// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import java.lang.Long.bitCount
import java.lang.Long.numberOfLeadingZeros

/**
 * Minimal persistent map contract used by persistent tree
 *
 * Values are non-null; `null` means that the key is absent. All implementations support non-negative [Long] keys and
 * reject negative keys. The vector implementations use keys as dense indexes, so ordinary monotonically allocated
 * marker IDs retain shallow lookup paths.
 *
 * Change the default in [empty] to switch the implementation used by existing `PersistentLongMap.empty()` call sites.
 */
internal interface PersistentLongMap<V : Any> {
  operator fun get(key: Long): V?

  /** Returns the value for a caller-validated non-negative [key]. */
  fun getUnchecked(key: Long): V?

  fun put(key: Long, value: V): PersistentLongMap<V>

  fun remove(key: Long): PersistentLongMap<V>

  /** Returns the number of entries. */
  fun size(): Int

  fun builder(): PersistentLongMapBuilder<V>

  /** Calls [action] for each entry in ascending key order. */
  fun forEachEntry(action: (Long, V) -> Unit)

  companion object {
    fun <V : Any> empty(implementation: PersistentLongMapImplementation = PersistentLongMapImplementation.VECTOR_64): PersistentLongMap<V> =
      implementation.empty()
  }
}

/**
 * Single-use mutable view used to batch changes before publishing one new [PersistentLongMap] version.
 *
 * A builder must not be accessed after [build]. Implementations may therefore retain builder-owned mutable nodes in
 * the resulting map without compromising persistence.
 */
internal interface PersistentLongMapBuilder<V : Any> {
  operator fun get(key: Long): V?

  /** Returns the value for a caller-validated non-negative [key]. */
  fun getUnchecked(key: Long): V?

  fun put(key: Long, value: V)

  fun remove(key: Long)

  fun build(): PersistentLongMap<V>
}

/** Available implementations of [PersistentLongMap]. */
internal enum class PersistentLongMapImplementation(private val emptyMap: PersistentLongMap<Any>) {
  /** Fixed-height 16-way trie: every key, small or large, is 16 levels deep. Baseline of the original implementation. */
  MAP_16(PersistentVector(bitsPerLevel = 4, fixedHeight = true)),
  VECTOR_32(PersistentVector(bitsPerLevel = 5)),
  VECTOR_64(PersistentVector(bitsPerLevel = 6)),
  /** 32-way page table over 128-element leaf pages. */
  PAGED_VECTOR_128(PersistentVector(bitsPerLevel = 5, leafBits = 7)),
  /** 32-way page table over 256-element leaf pages. */
  PAGED_VECTOR_256(PersistentVector(bitsPerLevel = 5, leafBits = 8)),
  CHAMP(PersistentLongChampMap()),
  CHAMP_64(PersistentLongChampMap(bitsPerLevel = 6));

  @Suppress("UNCHECKED_CAST")
  fun <V : Any> empty(): PersistentLongMap<V> = emptyMap as PersistentLongMap<V>
}

private fun requireNonNegativeKey(key: Long) {
  require(key >= 0) { "key must be non-negative" }
}

private fun <V : Any> PersistentLongMap<V>.mapToString(): String {
  val result = StringBuilder("{")
  forEachEntry { key, value ->
    if (result.length > 1) result.append(", ")
    result.append(key).append('=').append(value)
  }
  return result.append('}').toString()
}

/**
 * Shared base of all builders.
 *
 * The same class also serves as a one-shot editor behind every persistent `put`/`remove`: such an editor has a `null`
 * [owner] and therefore copies every node it touches (plain path copying), while a real builder has a unique token and
 * mutates the nodes it has already copied in place.
 */
internal abstract class PersistentLongMapEditor<V : Any>(
  /** Identity token of the nodes this editor may mutate in place; `null` means "copy every touched node". */
  @JvmField protected val owner: Any?,
) : PersistentLongMapBuilder<V> {
  private var built: Boolean = false

  /** Whether the result differs from the source map. */
  @JvmField protected var changed: Boolean = false

  protected fun owns(nodeOwner: Any?): Boolean = owner != null && nodeOwner === owner

  protected abstract fun find(key: Long): V?

  abstract fun putUnchecked(key: Long, value: V)

  /** Removes [key], which the caller has verified to be present. */
  abstract fun removeExisting(key: Long)

  abstract fun result(): PersistentLongMap<V>

  final override fun get(key: Long): V? {
    checkActive()
    requireNonNegativeKey(key)
    return find(key)
  }

  final override fun getUnchecked(key: Long): V? {
    checkActive()
    return find(key)
  }

  final override fun put(key: Long, value: V) {
    checkActive()
    requireNonNegativeKey(key)
    putUnchecked(key, value)
  }

  final override fun remove(key: Long) {
    checkActive()
    requireNonNegativeKey(key)
    if (find(key) != null) removeExisting(key)
  }

  final override fun build(): PersistentLongMap<V> {
    checkActive()
    built = true
    return result()
  }

  private fun checkActive() {
    check(!built) { "PersistentLongMapBuilder has already been built" }
  }
}

/**
 * Persistent radix trie that uses keys as dense indexes.
 *
 * Leaf nodes hold `1 shl leafBits` values and consume the lowest key bits; inner nodes hold `1 shl bitsPerLevel`
 * children and consume [bitsPerLevel] key bits each. One class therefore covers several layouts:
 * - a plain vector (`leafBits == bitsPerLevel`);
 * - a paged vector (wide leaf pages under a narrower page table), where an update copies one page and the inner path;
 * - a fixed-height trie ([fixedHeight]), whose root always covers the full key range.
 *
 * Without [fixedHeight] the trie grows and shrinks with its largest key, so small keys keep short lookup paths.
 * Every node counts its non-null slots, which makes emptiness checks and root trimming constant-time.
 */
internal class PersistentVector<V : Any> private constructor(
  private val bits: Int,
  private val leafBits: Int,
  /** Lowest root shift; non-zero only for fixed-height tries. */
  private val minShift: Int,
  private val root: Node?,
  /** Key bit offset consumed by [root]; `0` when the root is a leaf. Inner shifts are `leafBits + k * bits`. */
  private val shift: Int,
  private val size: Int,
) : PersistentLongMap<V> {
  constructor(bitsPerLevel: Int, leafBits: Int = bitsPerLevel, fixedHeight: Boolean = false) :
    this(bitsPerLevel, leafBits, minShift(bitsPerLevel, leafBits, fixedHeight), null, 0, 0)

  override operator fun get(key: Long): V? {
    requireNonNegativeKey(key)
    return find(root, shift, key)
  }

  override fun getUnchecked(key: Long): V? = find(root, shift, key)

  override fun put(key: Long, value: V): PersistentVector<V> {
    requireNonNegativeKey(key)
    return Editor(this, null).apply { putUnchecked(key, value) }.result()
  }

  override fun remove(key: Long): PersistentVector<V> {
    requireNonNegativeKey(key)
    if (find(root, shift, key) == null) return this
    return Editor(this, null).apply { removeExisting(key) }.result()
  }

  override fun size(): Int = size

  override fun builder(): PersistentLongMapBuilder<V> = Editor(this, Any())

  override fun forEachEntry(action: (Long, V) -> Unit) {
    forEachEntry(root ?: return, shift, 0L, action)
  }

  override fun toString(): String = mapToString()

  private fun forEachEntry(node: Node, shift: Int, prefix: Long, action: (Long, V) -> Unit) {
    val slots = node.slots
    if (shift == 0) return forEachLeafEntry(slots, prefix, action)
    val childShift = childShift(shift)
    for (slot in slots.indices) {
      val child = slots[slot] ?: continue
      forEachEntry(child as Node, childShift, prefix or (slot.toLong() shl shift), action)
    }
  }

  /** Kept separate from the recursive traversal so that the JIT compiles the hot leaf loop on its own. */
  private fun forEachLeafEntry(slots: Array<Any?>, prefix: Long, action: (Long, V) -> Unit) {
    for (slot in slots.indices) {
      val value = slots[slot] ?: continue
      @Suppress("UNCHECKED_CAST")
      action(prefix or slot.toLong(), value as V)
    }
  }

  private fun find(root: Node?, shift: Int, key: Long): V? {
    var node = root ?: return null
    var s = shift
    if (!fits(key, s)) return null
    val mask = (1 shl bits) - 1
    // Inner shifts step down by `bits` to `leafBits`; the next step falls below `leafBits` and reaches the leaf.
    while (s >= leafBits) {
      node = node.slots[(key ushr s).toInt() and mask] as Node? ?: return null
      s -= bits
    }
    @Suppress("UNCHECKED_CAST")
    return node.slots[key.toInt() and ((1 shl leafBits) - 1)] as V?
  }

  /** Whether a root node at [shift] covers [key]. */
  private fun fits(key: Long, shift: Int): Boolean = (key ushr shift) ushr (if (shift == 0) leafBits else bits) == 0L

  private fun childShift(shift: Int): Int = if (shift == leafBits) 0 else shift - bits

  private fun parentShift(shift: Int): Int = if (shift == 0) leafBits else shift + bits

  /** Returns the smallest root shift whose node covers [key]. */
  private fun requiredShift(key: Long): Int {
    val highestBit = Long.SIZE_BITS - 1 - numberOfLeadingZeros(key)
    val shift = if (highestBit < leafBits) 0 else leafBits + (highestBit - leafBits) / bits * bits
    return maxOf(shift, minShift)
  }

  private class Node(
    @JvmField val slots: Array<Any?>,
    /** Number of non-null [slots]. */
    @JvmField var count: Int,
    @JvmField val owner: Any?,
  )

  private class Editor<V : Any>(private val source: PersistentVector<V>, owner: Any?) : PersistentLongMapEditor<V>(owner) {
    private var root: Node? = source.root
    private var shift: Int = source.shift
    private var size: Int = source.size

    override fun find(key: Long): V? = source.find(root, shift, key)

    override fun putUnchecked(key: Long, value: V) {
      val current = root
      var s: Int
      var node: Node
      if (current != null && source.fits(key, shift)) {
        s = shift
        node = editable(current)
      }
      else if (current == null) {
        s = source.requiredShift(key)
        node = newNode(s)
      }
      else {
        // Grow: the old root becomes the leftmost descendant and stays untouched, since `key` lies to its right.
        val requiredShift = source.requiredShift(key)
        s = shift
        node = current
        while (s < requiredShift) {
          s = source.parentShift(s)
          val parent = newNode(s)
          parent.slots[0] = node
          parent.count = 1
          node = parent
        }
      }
      root = node
      shift = s

      val innerMask = (1 shl source.bits) - 1
      while (s != 0) {
        val slot = (key ushr s).toInt() and innerMask
        s = source.childShift(s)
        val child = node.slots[slot] as Node?
        val next = if (child == null) newNode(s).also { node.count++ } else editable(child)
        if (next !== child) node.slots[slot] = next
        node = next
      }

      val slot = key.toInt() and ((1 shl source.leafBits) - 1)
      if (node.slots[slot] == null) {
        node.count++
        size++
      }
      node.slots[slot] = value
      changed = true
    }

    override fun removeExisting(key: Long) {
      var s = shift
      var node = removeExisting(root!!, s, key)
      // Trim roots whose only child is the leftmost one.
      while (node != null && s > source.minShift && node.count == 1 && node.slots[0] != null) {
        node = node.slots[0] as Node
        s = source.childShift(s)
      }
      root = node
      shift = if (node == null) 0 else s
      size--
      changed = true
    }

    /** Returns the updated [node], or `null` when it becomes empty. */
    private fun removeExisting(node: Node, shift: Int, key: Long): Node? {
      val slot: Int
      val replacement: Node?
      if (shift == 0) {
        slot = key.toInt() and ((1 shl source.leafBits) - 1)
        replacement = null
      }
      else {
        slot = (key ushr shift).toInt() and ((1 shl source.bits) - 1)
        replacement = removeExisting(node.slots[slot] as Node, source.childShift(shift), key)
      }
      if (replacement == null && node.count == 1) return null

      val result = editable(node)
      if (replacement == null) result.count--
      if (result.slots[slot] !== replacement) result.slots[slot] = replacement
      return result
    }

    private fun newNode(shift: Int): Node = Node(arrayOfNulls(1 shl (if (shift == 0) source.leafBits else source.bits)), 0, owner)

    private fun editable(node: Node): Node = if (owns(node.owner)) node else Node(node.slots.copyOf(), node.count, owner)

    override fun result(): PersistentVector<V> =
      if (changed) PersistentVector(source.bits, source.leafBits, source.minShift, root, shift, size) else source
  }

  private companion object {
    fun minShift(bitsPerLevel: Int, leafBits: Int, fixedHeight: Boolean): Int {
      require(bitsPerLevel in 1..6) { "bitsPerLevel must be between 1 and 6" }
      require(leafBits in 1..30) { "leafBits must be between 1 and 30" }
      // The highest inner shift below 64, i.e. the shift of a root that covers every non-negative key.
      return if (fixedHeight) leafBits + (Long.SIZE_BITS - 1 - leafBits) / bitsPerLevel * bitsPerLevel else 0
    }
  }
}

/**
 * Bitmap-compressed hash trie specialized for primitive [Long] keys.
 *
 * Each level consumes the configured number of bits from the mixed key. An entry stays directly in its current node
 * until another key selects the same branch; the two entries are then moved into a child node that examines the next
 * group of bits. The `dataMap` and `nodeMap` bitmaps identify direct entries and child nodes respectively, so empty
 * branches occupy no array slots. A single [Long] bitmap supports layouts up to 64 branches (six bits per level).
 *
 * Keys are mixed with a bijective 64-bit function, so distinct keys never require a boxed collision bucket. Nodes keep
 * direct keys in a primitive [LongArray], while one compact object array holds their values followed by child nodes.
 * Persistent updates copy only the affected path; the builder may mutate nodes carrying its unique ownership token.
 *
 * Invariant: a child node never consists of a single direct entry; such a node is inlined into its parent on removal.
 */
internal class PersistentLongChampMap<V : Any> private constructor(
  private val bits: Int,
  private val root: Node?,
  private val size: Int,
) : PersistentLongMap<V> {
  constructor(bitsPerLevel: Int = DEFAULT_BITS_PER_LEVEL) : this(checkBitsPerLevel(bitsPerLevel), null, 0)

  override operator fun get(key: Long): V? {
    requireNonNegativeKey(key)
    return find(root, key)
  }

  override fun getUnchecked(key: Long): V? = find(root, key)

  override fun put(key: Long, value: V): PersistentLongChampMap<V> {
    requireNonNegativeKey(key)
    return Editor(this, null).apply { putUnchecked(key, value) }.result()
  }

  override fun remove(key: Long): PersistentLongChampMap<V> {
    requireNonNegativeKey(key)
    if (find(root, key) == null) return this
    return Editor(this, null).apply { removeExisting(key) }.result()
  }

  override fun size(): Int = size

  override fun builder(): PersistentLongMapBuilder<V> = Editor(this, Any())

  override fun forEachEntry(action: (Long, V) -> Unit) {
    val root = root ?: return
    // Entries are ordered by hash; sort the primitive keys and look the values up instead of boxing into a TreeMap.
    val keys = LongArray(size)
    collectKeys(root, keys, 0)
    keys.sort()
    for (key in keys) {
      action(key, find(root, key)!!)
    }
  }

  override fun toString(): String = mapToString()

  private fun collectKeys(node: Node, target: LongArray, offset: Int): Int {
    val keys = node.keys
    keys.copyInto(target, offset)
    var next = offset + keys.size
    for (index in keys.size until node.content.size) {
      next = collectKeys(node.content[index] as Node, target, next)
    }
    return next
  }

  private fun find(root: Node?, key: Long): V? {
    var node = root ?: return null
    val hash = mixKey(key)
    val mask = (1 shl bits) - 1
    var shift = 0
    while (true) {
      val bit = 1L shl ((hash ushr shift).toInt() and mask)
      if (node.dataMap and bit != 0L) {
        val index = bitmapIndex(node.dataMap, bit)
        @Suppress("UNCHECKED_CAST")
        return if (node.keys[index] == key) node.content[index] as V else null
      }
      if (node.nodeMap and bit == 0L) return null
      node = node.content[node.keys.size + bitmapIndex(node.nodeMap, bit)] as Node
      shift += bits
    }
  }

  private class Node(
    /** Bitmap of branches represented by direct entries in [keys] and the value prefix of [content]. */
    @JvmField var dataMap: Long,

    /** Bitmap of branches represented by child nodes in the suffix of [content]; disjoint from [dataMap]. */
    @JvmField var nodeMap: Long,

    /** Direct keys ordered by their branch bit; its size is the population count of [dataMap]. Never mutated in place. */
    @JvmField var keys: LongArray,

    /** Direct values for [keys], followed by child nodes ordered by their bits in [nodeMap]. */
    @JvmField var content: Array<Any?>,

    /**
     * Identity token of the builder allowed to mutate this node. Published nodes may retain an old token, but every
     * subsequent builder uses a new token and therefore copies the node before changing it.
     */
    @JvmField val owner: Any?,
  )

  private class Editor<V : Any>(private val source: PersistentLongChampMap<V>, owner: Any?) : PersistentLongMapEditor<V>(owner) {
    private val bits: Int = source.bits
    private var root: Node? = source.root
    private var size: Int = source.size

    override fun find(key: Long): V? = source.find(root, key)

    override fun putUnchecked(key: Long, value: V) {
      root = put(root, 0, key, mixKey(key), value)
      changed = true
    }

    override fun removeExisting(key: Long) {
      root = remove(root!!, 0, key, mixKey(key))
      size--
      changed = true
    }

    override fun result(): PersistentLongChampMap<V> = if (changed) PersistentLongChampMap(bits, root, size) else source

    private fun slot(hash: Long, shift: Int): Int = (hash ushr shift).toInt() and ((1 shl bits) - 1)

    private fun put(node: Node?, shift: Int, key: Long, hash: Long, value: Any): Node {
      val bit = 1L shl slot(hash, shift)
      if (node == null) {
        size++
        return Node(bit, 0L, longArrayOf(key), arrayOf(value), owner)
      }

      if (node.dataMap and bit != 0L) {
        val dataIndex = bitmapIndex(node.dataMap, bit)
        val existingKey = node.keys[dataIndex]
        val result = editable(node)
        if (existingKey == key) {
          result.content[dataIndex] = value
          return result
        }

        // Replace the direct entry with a child holding both entries; `content` keeps its length.
        size++
        val child = merge(existingKey, node.content[dataIndex]!!, mixKey(existingKey), key, value, hash, shift + bits)
        val childOffset = node.keys.size - 1 + bitmapIndex(node.nodeMap, bit)
        val content = result.content
        System.arraycopy(content, dataIndex + 1, content, dataIndex, childOffset - dataIndex)
        content[childOffset] = child
        result.keys = node.keys.removedAt(dataIndex)
        result.dataMap = node.dataMap xor bit
        result.nodeMap = node.nodeMap or bit
        return result
      }

      if (node.nodeMap and bit != 0L) {
        val childOffset = node.keys.size + bitmapIndex(node.nodeMap, bit)
        val child = node.content[childOffset] as Node
        val updatedChild = put(child, shift + bits, key, hash, value)
        if (updatedChild === child && owns(node.owner)) return node
        val result = editable(node)
        result.content[childOffset] = updatedChild
        return result
      }

      size++
      val dataIndex = bitmapIndex(node.dataMap, bit)
      return update(node, node.dataMap or bit, node.nodeMap, node.keys.inserted(dataIndex, key), node.content.inserted(dataIndex, value))
    }

    /** Returns the updated [node] without [key], or `null` when it becomes empty. */
    private fun remove(node: Node, shift: Int, key: Long, hash: Long): Node? {
      val bit = 1L shl slot(hash, shift)

      if (node.dataMap and bit != 0L) {
        if (node.nodeMap == 0L && node.keys.size == 1) return null
        val dataIndex = bitmapIndex(node.dataMap, bit)
        return update(node, node.dataMap xor bit, node.nodeMap, node.keys.removedAt(dataIndex), node.content.removedAt(dataIndex))
      }

      val childOffset = node.keys.size + bitmapIndex(node.nodeMap, bit)
      val child = remove(node.content[childOffset] as Node, shift + bits, key, hash)

      if (child == null) {
        if (node.dataMap == 0L && node.nodeMap == bit) return null
        return update(node, node.dataMap, node.nodeMap xor bit, node.keys, node.content.removedAt(childOffset))
      }

      val result = editable(node)
      if (child.nodeMap == 0L && child.keys.size == 1) {
        // Inline the remaining single entry of the child; `content` keeps its length.
        val dataIndex = bitmapIndex(node.dataMap, bit)
        val content = result.content
        System.arraycopy(content, dataIndex, content, dataIndex + 1, childOffset - dataIndex)
        content[dataIndex] = child.content[0]
        result.keys = node.keys.inserted(dataIndex, child.keys[0])
        result.dataMap = node.dataMap or bit
        result.nodeMap = node.nodeMap xor bit
      }
      else if (result.content[childOffset] !== child) {
        result.content[childOffset] = child
      }
      return result
    }

    private fun merge(firstKey: Long, firstValue: Any, firstHash: Long, secondKey: Long, secondValue: Any, secondHash: Long, shift: Int): Node {
      check(shift < Long.SIZE_BITS) { "Distinct long keys produced the same mixed hash" }
      val firstSlot = slot(firstHash, shift)
      val secondSlot = slot(secondHash, shift)
      if (firstSlot == secondSlot) {
        val child = merge(firstKey, firstValue, firstHash, secondKey, secondValue, secondHash, shift + bits)
        return Node(0L, 1L shl firstSlot, EMPTY_KEYS, arrayOf(child), owner)
      }
      val dataMap = (1L shl firstSlot) or (1L shl secondSlot)
      return if (firstSlot < secondSlot) {
        Node(dataMap, 0L, longArrayOf(firstKey, secondKey), arrayOf(firstValue, secondValue), owner)
      }
      else {
        Node(dataMap, 0L, longArrayOf(secondKey, firstKey), arrayOf(secondValue, firstValue), owner)
      }
    }

    /** Returns [node] itself if owned, otherwise a copy with its own [Node.content] (keys are immutable and shared). */
    private fun editable(node: Node): Node =
      if (owns(node.owner)) node else Node(node.dataMap, node.nodeMap, node.keys, node.content.copyOf(), owner)

    /** Returns [node] with the given freshly allocated state, reusing the node object if owned. */
    private fun update(node: Node, dataMap: Long, nodeMap: Long, keys: LongArray, content: Array<Any?>): Node {
      if (!owns(node.owner)) return Node(dataMap, nodeMap, keys, content, owner)
      node.dataMap = dataMap
      node.nodeMap = nodeMap
      node.keys = keys
      node.content = content
      return node
    }
  }

  private companion object {
    const val DEFAULT_BITS_PER_LEVEL: Int = 5
    val EMPTY_KEYS: LongArray = LongArray(0)

    fun checkBitsPerLevel(bitsPerLevel: Int): Int {
      require(bitsPerLevel in 1..6) { "bitsPerLevel must be between 1 and 6" }
      return bitsPerLevel
    }

    fun mixKey(key: Long): Long {
      var mixed = key
      mixed = (mixed xor (mixed ushr 30)) * -4658895280553007687L
      mixed = (mixed xor (mixed ushr 27)) * -7723592293110705685L
      return mixed xor (mixed ushr 31)
    }

    fun bitmapIndex(bitmap: Long, bit: Long): Int = bitCount(bitmap and (bit - 1))
  }
}

private fun LongArray.inserted(index: Int, value: Long): LongArray {
  val result = LongArray(size + 1)
  copyInto(result, 0, 0, index)
  result[index] = value
  copyInto(result, index + 1, index)
  return result
}

private fun LongArray.removedAt(index: Int): LongArray {
  val result = LongArray(size - 1)
  copyInto(result, 0, 0, index)
  copyInto(result, index, index + 1)
  return result
}

private fun Array<Any?>.inserted(index: Int, value: Any?): Array<Any?> {
  val result = arrayOfNulls<Any>(size + 1)
  copyInto(result, 0, 0, index)
  result[index] = value
  copyInto(result, index + 1, index)
  return result
}

private fun Array<Any?>.removedAt(index: Int): Array<Any?> {
  val result = arrayOfNulls<Any>(size - 1)
  copyInto(result, 0, 0, index)
  copyInto(result, index, index + 1)
  return result
}
