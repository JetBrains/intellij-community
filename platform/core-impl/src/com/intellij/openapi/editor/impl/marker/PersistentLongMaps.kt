// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

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

  fun builder(): PersistentLongMapBuilder<V> = CopyingPersistentLongMapBuilder(this)

  companion object {
    private val EMPTY_MAP_16 = PersistentLongMap16<Any>()
    private val EMPTY_VECTOR_32 = PersistentVector32<Any>()
    private val EMPTY_VECTOR_64 = PersistentVector64<Any>()
    private val EMPTY_PAGED_VECTOR_128 = PersistentPagedVector128<Any>()
    private val EMPTY_PAGED_VECTOR_256 = PersistentPagedVector256<Any>()
    private val EMPTY_CHAMP = PersistentLongChampMap<Any>()
    private val EMPTY_CHAMP_64 = PersistentLongChampMap<Any>(bitsPerLevel = 6)

    @Suppress("UNCHECKED_CAST")
    fun <V : Any> empty(implementation: PersistentLongMapImplementation): PersistentLongMap<V> = when (implementation) {
      PersistentLongMapImplementation.MAP_16 -> EMPTY_MAP_16
      PersistentLongMapImplementation.VECTOR_32 -> EMPTY_VECTOR_32
      PersistentLongMapImplementation.VECTOR_64 -> EMPTY_VECTOR_64
      PersistentLongMapImplementation.PAGED_VECTOR_128 -> EMPTY_PAGED_VECTOR_128
      PersistentLongMapImplementation.PAGED_VECTOR_256 -> EMPTY_PAGED_VECTOR_256
      PersistentLongMapImplementation.CHAMP -> EMPTY_CHAMP
      PersistentLongMapImplementation.CHAMP_64 -> EMPTY_CHAMP_64
    } as PersistentLongMap<V>
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

private class CopyingPersistentLongMapBuilder<V : Any>(
  private var map: PersistentLongMap<V>,
) : PersistentLongMapBuilder<V> {
  private var active = true

  override fun get(key: Long): V? {
    checkActive()
    return map[key]
  }

  override fun getUnchecked(key: Long): V? {
    checkActive()
    return map.getUnchecked(key)
  }

  override fun put(key: Long, value: V) {
    checkActive()
    map = map.put(key, value)
  }

  override fun remove(key: Long) {
    checkActive()
    map = map.remove(key)
  }

  override fun build(): PersistentLongMap<V> {
    checkActive()
    active = false
    return map
  }

  private fun checkActive() {
    check(active) { "PersistentLongMapBuilder has already been built" }
  }
}

/** Available implementations of [PersistentLongMap]. */
internal enum class PersistentLongMapImplementation {
  MAP_16,
  VECTOR_32,
  VECTOR_64,
  PAGED_VECTOR_128,
  PAGED_VECTOR_256,
  CHAMP,
  CHAMP_64,
}

private fun requireNonNegativeKey(key: Long): Long {
  require(key >= 0) { "key must be non-negative" }
  return key
}

/**
 * Fixed-height 16-way radix trie consuming four key bits per level.
 *
 * Every operation traverses at most 16 levels, independently of the number and distribution of keys. This is the
 * closest replacement for the original `PersistentLongMap` implementation.
 */
internal class PersistentLongMap16<V : Any> private constructor(private val root: Branch?) : PersistentLongMap<V> {
  constructor() : this(null)

  override operator fun get(key: Long): V? = getUnchecked(requireNonNegativeKey(key))

  override fun getUnchecked(key: Long): V? {
    var branch = root ?: return null

    for (depth in 0 until LEVELS) {
      val child = branch.children[index(key, depth)] ?: return null
      if (depth == LEVELS - 1) {
        @Suppress("UNCHECKED_CAST")
        return child as V
      }
      branch = child as Branch
    }

    return null
  }

  override fun put(key: Long, value: V): PersistentLongMap16<V> {
    requireNonNegativeKey(key)
    return PersistentLongMap16(put(root, key, value, 0))
  }

  override fun remove(key: Long): PersistentLongMap16<V> {
    requireNonNegativeKey(key)
    val result = remove(root, key, 0)
    return if (!result.removed) this else PersistentLongMap16(result.branch)
  }

  override fun builder(): PersistentLongMapBuilder<V> = Builder(this)

  private class Branch(val children: Array<Any?>, val owner: Any? = null)

  private data class Removal(val branch: Branch?, val removed: Boolean)

  private class Builder<V : Any>(private val source: PersistentLongMap16<V>) : PersistentLongMapBuilder<V> {
    private val owner = Any()
    private var root = source.root
    private var dirty = false
    private var active = true

    override fun get(key: Long): V? {
      checkActive()
      return find(requireNonNegativeKey(key))
    }

    override fun getUnchecked(key: Long): V? {
      checkActive()
      return find(key)
    }

    private fun find(key: Long): V? {
      var branch = root ?: return null

      for (depth in 0 until LEVELS) {
        val child = branch.children[index(key, depth)] ?: return null
        if (depth == LEVELS - 1) {
          @Suppress("UNCHECKED_CAST")
          return child as V
        }
        branch = child as Branch
      }
      return null
    }

    override fun put(key: Long, value: V) {
      checkActive()
      requireNonNegativeKey(key)
      root = put(root, key, value, 0)
      dirty = true
    }

    override fun remove(key: Long) {
      checkActive()
      requireNonNegativeKey(key)
      val result = remove(root, key, 0)
      if (!result.removed) return
      root = result.branch
      dirty = true
    }

    override fun build(): PersistentLongMap<V> {
      checkActive()
      active = false
      return if (dirty) PersistentLongMap16(root) else source
    }

    private fun put(branch: Branch?, key: Long, value: V, depth: Int): Branch {
      val editable = editable(branch)
      val index = index(key, depth)
      editable.children[index] = if (depth == LEVELS - 1) {
        value
      }
      else {
        put(editable.children[index] as? Branch, key, value, depth + 1)
      }
      return editable
    }

    private fun remove(branch: Branch?, key: Long, depth: Int): Removal {
      branch ?: return Removal(null, false)
      val index = index(key, depth)
      val child = branch.children[index] ?: return Removal(branch, false)
      val editable = editable(branch)

      if (depth == LEVELS - 1) {
        editable.children[index] = null
      }
      else {
        val result = remove(child as Branch, key, depth + 1)
        if (!result.removed) return Removal(branch, false)
        editable.children[index] = result.branch
      }
      return Removal(if (isEmpty(editable.children)) null else editable, true)
    }

    private fun editable(branch: Branch?): Branch {
      if (branch?.owner === owner) return branch
      return Branch(branch?.children?.copyOf() ?: arrayOfNulls(WIDTH), owner)
    }

    private fun checkActive() {
      check(active) { "PersistentLongMapBuilder has already been built" }
    }
  }

  companion object {
    private const val BITS: Int = 4
    private const val WIDTH: Int = 1 shl BITS
    private const val MASK: Long = (WIDTH - 1).toLong()
    private const val LEVELS: Int = Long.SIZE_BITS / BITS

    private fun index(key: Long, depth: Int): Int {
      val shift = (LEVELS - depth - 1) * BITS
      return ((key ushr shift) and MASK).toInt()
    }

    private fun <V : Any> put(branch: Branch?, key: Long, value: V, depth: Int): Branch {
      val children = branch?.children?.copyOf() ?: arrayOfNulls(WIDTH)
      val index = index(key, depth)
      children[index] = if (depth == LEVELS - 1) {
        value
      }
      else {
        put(children[index] as? Branch, key, value, depth + 1)
      }
      return Branch(children)
    }

    private fun remove(branch: Branch?, key: Long, depth: Int): Removal {
      branch ?: return Removal(null, false)
      val index = index(key, depth)
      val child = branch.children[index] ?: return Removal(branch, false)
      val children = branch.children.copyOf()

      if (depth == LEVELS - 1) {
        children[index] = null
      }
      else {
        val result = remove(child as Branch, key, depth + 1)
        if (!result.removed) return Removal(branch, false)
        children[index] = result.branch
      }

      return Removal(if (isEmpty(children)) null else Branch(children), true)
    }

    private fun isEmpty(children: Array<Any?>): Boolean {
      for (child in children) {
        if (child != null) return false
      }
      return true
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
 */
internal class PersistentLongChampMap<V : Any> private constructor(
  private val layout: Layout,
  private val root: Node?,
) : PersistentLongMap<V> {
  constructor(bitsPerLevel: Int = DEFAULT_BITS_PER_LEVEL) : this(Layout(bitsPerLevel), null)

  override operator fun get(key: Long): V? = getUnchecked(requireNonNegativeKey(key))

  override fun getUnchecked(key: Long): V? = find(layout, root, key, mixKey(key))

  override fun put(key: Long, value: V): PersistentLongChampMap<V> {
    val checkedKey = requireNonNegativeKey(key)
    return PersistentLongChampMap(layout, put(layout, root, 0, checkedKey, mixKey(checkedKey), value))
  }

  override fun remove(key: Long): PersistentLongChampMap<V> {
    val checkedKey = requireNonNegativeKey(key)
    val result = remove(layout, root, 0, checkedKey, mixKey(checkedKey))
    return if (!result.removed) this else PersistentLongChampMap(layout, result.node)
  }

  override fun builder(): PersistentLongMapBuilder<V> = Builder(this)

  private class Node(
    /** Bitmap of branches represented by direct entries in [keys] and the value prefix of [content]. */
    var dataMap: Long,

    /** Bitmap of branches represented by child nodes in the suffix of [content]; disjoint from [dataMap]. */
    var nodeMap: Long,

    /** Direct keys ordered by their branch bit; its size is the population count of [dataMap]. */
    var keys: LongArray,

    /** Direct values for [keys], followed by child nodes ordered by their bits in [nodeMap]. */
    var content: Array<Any?>,

    /**
     * Identity token of the builder allowed to mutate this node. Published nodes may retain an old token, but every
     * subsequent builder uses a new token and therefore copies the node before changing it.
     */
    val owner: Any? = null,
  )

  private data class Removal(val node: Node?, val removed: Boolean)

  private class Builder<V : Any>(private val source: PersistentLongChampMap<V>) : PersistentLongMapBuilder<V> {
    private val owner = Any()
    private var root = source.root
    private var dirty = false
    private var active = true

    override fun get(key: Long): V? {
      checkActive()
      val checkedKey = requireNonNegativeKey(key)
      return find(source.layout, root, checkedKey, mixKey(checkedKey))
    }

    override fun getUnchecked(key: Long): V? {
      checkActive()
      return find(source.layout, root, key, mixKey(key))
    }

    override fun put(key: Long, value: V) {
      checkActive()
      val checkedKey = requireNonNegativeKey(key)
      root = put(root, 0, checkedKey, mixKey(checkedKey), value)
      dirty = true
    }

    override fun remove(key: Long) {
      checkActive()
      val checkedKey = requireNonNegativeKey(key)
      val result = remove(root, 0, checkedKey, mixKey(checkedKey))
      if (!result.removed) return
      root = result.node
      dirty = true
    }

    override fun build(): PersistentLongMap<V> {
      checkActive()
      active = false
      return if (dirty) PersistentLongChampMap(source.layout, root) else source
    }

    private fun put(node: Node?, shift: Int, key: Long, hash: Long, value: Any): Node {
      if (node == null) return singletonNode(source.layout.branchBit(hash, shift), key, value, owner)
      val editable = editable(node)
      val bit = source.layout.branchBit(hash, shift)

      if (editable.dataMap and bit != 0L) {
        val dataIndex = bitmapIndex(editable.dataMap, bit)
        val existingKey = editable.keys[dataIndex]
        if (existingKey == key) {
          editable.content[dataIndex] = value
        }
        else {
          val child = mergeEntries(
            source.layout,
            existingKey,
            editable.content[dataIndex]!!,
            mixKey(existingKey),
            key,
            value,
            hash,
            shift + source.layout.bitsPerLevel,
            owner,
          )
          editable.dataMap = editable.dataMap xor bit
          editable.nodeMap = editable.nodeMap or bit
          editable.keys = removeAt(editable.keys, dataIndex)
          editable.content = removeAt(editable.content, dataIndex)
          val childIndex = bitmapIndex(editable.nodeMap, bit)
          editable.content = insert(editable.content, editable.keys.size + childIndex, child)
        }
        return editable
      }

      if (editable.nodeMap and bit != 0L) {
        val childIndex = bitmapIndex(editable.nodeMap, bit)
        val childOffset = editable.keys.size + childIndex
        editable.content[childOffset] = put(
          editable.content[childOffset] as Node,
          shift + source.layout.bitsPerLevel,
          key,
          hash,
          value,
        )
        return editable
      }

      val dataIndex = bitmapIndex(editable.dataMap, bit)
      editable.dataMap = editable.dataMap or bit
      editable.keys = insert(editable.keys, dataIndex, key)
      editable.content = insert(editable.content, dataIndex, value)
      return editable
    }

    private fun remove(node: Node?, shift: Int, key: Long, hash: Long): Removal {
      node ?: return Removal(null, false)
      val bit = source.layout.branchBit(hash, shift)

      if (node.dataMap and bit != 0L) {
        val dataIndex = bitmapIndex(node.dataMap, bit)
        if (node.keys[dataIndex] != key) return Removal(node, false)
        val editable = editable(node)
        editable.dataMap = editable.dataMap xor bit
        editable.keys = removeAt(editable.keys, dataIndex)
        editable.content = removeAt(editable.content, dataIndex)
        return Removal(if (isEmpty(editable)) null else editable, true)
      }

      if (node.nodeMap and bit == 0L) return Removal(node, false)
      val childIndex = bitmapIndex(node.nodeMap, bit)
      val childOffset = node.keys.size + childIndex
      val result = remove(node.content[childOffset] as Node, shift + source.layout.bitsPerLevel, key, hash)
      if (!result.removed) return Removal(node, false)

      val editable = editable(node)
      val updatedChild = result.node
      if (updatedChild == null) {
        editable.nodeMap = editable.nodeMap xor bit
        editable.content = removeAt(editable.content, childOffset)
      }
      else if (isSingletonData(updatedChild)) {
        val dataIndex = bitmapIndex(editable.dataMap, bit)
        editable.dataMap = editable.dataMap or bit
        editable.nodeMap = editable.nodeMap xor bit
        editable.keys = insert(editable.keys, dataIndex, updatedChild.keys[0])
        editable.content = removeAt(editable.content, childOffset)
        editable.content = insert(editable.content, dataIndex, updatedChild.content[0]!!)
      }
      else {
        editable.content[childOffset] = updatedChild
      }
      return Removal(if (isEmpty(editable)) null else editable, true)
    }

    private fun editable(node: Node): Node {
      if (node.owner === owner) return node
      return Node(node.dataMap, node.nodeMap, node.keys.copyOf(), node.content.copyOf(), owner)
    }

    private fun checkActive() {
      check(active) { "PersistentLongMapBuilder has already been built" }
    }
  }

  private class Layout(val bitsPerLevel: Int) {
    private val mask: Int
    val maxShift: Int

    init {
      require(bitsPerLevel in 1..MAX_BITS_PER_LEVEL) {
        "bitsPerLevel must be between 1 and $MAX_BITS_PER_LEVEL"
      }
      mask = (1 shl bitsPerLevel) - 1
      maxShift = (Long.SIZE_BITS - 1) / bitsPerLevel * bitsPerLevel
    }

    fun branchBit(hash: Long, shift: Int): Long = 1L shl slot(hash, shift)

    fun slot(hash: Long, shift: Int): Int = (hash ushr shift).toInt() and mask
  }

  companion object {
    private const val DEFAULT_BITS_PER_LEVEL: Int = 5
    private const val MAX_BITS_PER_LEVEL: Int = 6

    private fun mixKey(key: Long): Long {
      var mixed = key
      mixed = (mixed xor (mixed ushr 30)) * -4658895280553007687L
      mixed = (mixed xor (mixed ushr 27)) * -7723592293110705685L
      return mixed xor (mixed ushr 31)
    }

    private fun bitmapIndex(bitmap: Long, bit: Long): Int = java.lang.Long.bitCount(bitmap and (bit - 1))

    private fun <V : Any> find(layout: Layout, root: Node?, key: Long, hash: Long): V? {
      var node = root ?: return null
      var shift = 0
      while (true) {
        val bit = layout.branchBit(hash, shift)
        if (node.dataMap and bit != 0L) {
          val dataIndex = bitmapIndex(node.dataMap, bit)
          if (node.keys[dataIndex] != key) return null
          @Suppress("UNCHECKED_CAST")
          return node.content[dataIndex] as V
        }
        if (node.nodeMap and bit == 0L) return null
        val childIndex = bitmapIndex(node.nodeMap, bit)
        node = node.content[node.keys.size + childIndex] as Node
        shift += layout.bitsPerLevel
      }
    }

    private fun put(layout: Layout, node: Node?, shift: Int, key: Long, hash: Long, value: Any): Node {
      if (node == null) return singletonNode(layout.branchBit(hash, shift), key, value)
      val bit = layout.branchBit(hash, shift)

      if (node.dataMap and bit != 0L) {
        val dataIndex = bitmapIndex(node.dataMap, bit)
        val existingKey = node.keys[dataIndex]
        if (existingKey == key) {
          val content = node.content.copyOf()
          content[dataIndex] = value
          return Node(node.dataMap, node.nodeMap, node.keys, content)
        }

        val child = mergeEntries(
          layout,
          existingKey,
          node.content[dataIndex]!!,
          mixKey(existingKey),
          key,
          value,
          hash,
          shift + layout.bitsPerLevel,
        )
        val keys = removeAt(node.keys, dataIndex)
        var content = removeAt(node.content, dataIndex)
        val childIndex = bitmapIndex(node.nodeMap, bit)
        content = insert(content, keys.size + childIndex, child)
        return Node(node.dataMap xor bit, node.nodeMap or bit, keys, content)
      }

      if (node.nodeMap and bit != 0L) {
        val childIndex = bitmapIndex(node.nodeMap, bit)
        val childOffset = node.keys.size + childIndex
        val content = node.content.copyOf()
        content[childOffset] = put(layout, content[childOffset] as Node, shift + layout.bitsPerLevel, key, hash, value)
        return Node(node.dataMap, node.nodeMap, node.keys, content)
      }

      val dataIndex = bitmapIndex(node.dataMap, bit)
      return Node(
        node.dataMap or bit,
        node.nodeMap,
        insert(node.keys, dataIndex, key),
        insert(node.content, dataIndex, value),
      )
    }

    private fun remove(layout: Layout, node: Node?, shift: Int, key: Long, hash: Long): Removal {
      node ?: return Removal(null, false)
      val bit = layout.branchBit(hash, shift)

      if (node.dataMap and bit != 0L) {
        val dataIndex = bitmapIndex(node.dataMap, bit)
        if (node.keys[dataIndex] != key) return Removal(node, false)
        val dataMap = node.dataMap xor bit
        if (dataMap == 0L && node.nodeMap == 0L) return Removal(null, true)
        return Removal(Node(dataMap, node.nodeMap, removeAt(node.keys, dataIndex), removeAt(node.content, dataIndex)), true)
      }

      if (node.nodeMap and bit == 0L) return Removal(node, false)
      val childIndex = bitmapIndex(node.nodeMap, bit)
      val childOffset = node.keys.size + childIndex
      val result = remove(layout, node.content[childOffset] as Node, shift + layout.bitsPerLevel, key, hash)
      if (!result.removed) return Removal(node, false)

      val updatedChild = result.node
      if (updatedChild == null) {
        val nodeMap = node.nodeMap xor bit
        if (node.dataMap == 0L && nodeMap == 0L) return Removal(null, true)
        return Removal(Node(node.dataMap, nodeMap, node.keys, removeAt(node.content, childOffset)), true)
      }

      if (isSingletonData(updatedChild)) {
        val dataIndex = bitmapIndex(node.dataMap, bit)
        val keys = insert(node.keys, dataIndex, updatedChild.keys[0])
        var content = removeAt(node.content, childOffset)
        content = insert(content, dataIndex, updatedChild.content[0]!!)
        return Removal(Node(node.dataMap or bit, node.nodeMap xor bit, keys, content), true)
      }

      val content = node.content.copyOf()
      content[childOffset] = updatedChild
      return Removal(Node(node.dataMap, node.nodeMap, node.keys, content), true)
    }

    private fun mergeEntries(
      layout: Layout,
      firstKey: Long,
      firstValue: Any,
      firstHash: Long,
      secondKey: Long,
      secondValue: Any,
      secondHash: Long,
      shift: Int,
      owner: Any? = null,
    ): Node {
      check(shift <= layout.maxShift) { "Distinct long keys produced the same mixed hash" }
      val firstBit = layout.branchBit(firstHash, shift)
      val secondBit = layout.branchBit(secondHash, shift)
      if (firstBit == secondBit) {
        val child = mergeEntries(
          layout,
          firstKey,
          firstValue,
          firstHash,
          secondKey,
          secondValue,
          secondHash,
          shift + layout.bitsPerLevel,
          owner,
        )
        return Node(0L, firstBit, LongArray(0), arrayOf<Any?>(child), owner)
      }

      val firstSlot = layout.slot(firstHash, shift)
      val secondSlot = layout.slot(secondHash, shift)
      val keys: LongArray
      val content: Array<Any?>
      if (firstSlot < secondSlot) {
        keys = longArrayOf(firstKey, secondKey)
        content = arrayOf(firstValue, secondValue)
      }
      else {
        keys = longArrayOf(secondKey, firstKey)
        content = arrayOf(secondValue, firstValue)
      }
      return Node(firstBit or secondBit, 0L, keys, content, owner)
    }

    private fun singletonNode(bit: Long, key: Long, value: Any, owner: Any? = null): Node {
      return Node(bit, 0L, longArrayOf(key), arrayOf(value), owner)
    }

    private fun isEmpty(node: Node): Boolean = node.dataMap == 0L && node.nodeMap == 0L

    private fun isSingletonData(node: Node): Boolean = node.nodeMap == 0L && node.keys.size == 1

    private fun insert(array: LongArray, index: Int, value: Long): LongArray {
      val result = LongArray(array.size + 1)
      array.copyInto(result, 0, 0, index)
      result[index] = value
      array.copyInto(result, index + 1, index)
      return result
    }

    private fun removeAt(array: LongArray, index: Int): LongArray {
      val result = LongArray(array.size - 1)
      array.copyInto(result, 0, 0, index)
      array.copyInto(result, index, index + 1)
      return result
    }

    private fun insert(array: Array<Any?>, index: Int, value: Any): Array<Any?> {
      val result = arrayOfNulls<Any>(array.size + 1)
      array.copyInto(result, 0, 0, index)
      result[index] = value
      array.copyInto(result, index + 1, index)
      return result
    }

    private fun removeAt(array: Array<Any?>, index: Int): Array<Any?> {
      val result = arrayOfNulls<Any>(array.size - 1)
      array.copyInto(result, 0, 0, index)
      array.copyInto(result, index, index + 1)
      return result
    }
  }
}

/**
 * Persistent 32-way indexed vector.
 *
 * A key `k` is stored at index `k`. For 50,000 marker IDs, lookups traverse four array levels.
 */
internal class PersistentVector32<V : Any> private constructor(
  private val root: Node?,
  private val shift: Int
) : PersistentLongMap<V> {
  constructor() : this(null, 0)

  override operator fun get(key: Long): V? = getUnchecked(requireNonNegativeKey(key))

  override fun getUnchecked(key: Long): V? = get(root, shift, key)

  override fun put(key: Long, value: V): PersistentVector32<V> {
    val index = requireNonNegativeKey(key)
    val updated = put(root, shift, index, value)
    return PersistentVector32(updated.root, updated.shift)
  }

  override fun remove(key: Long): PersistentVector32<V> {
    val index = requireNonNegativeKey(key)
    val updated = remove(root, shift, index)
    return if (!updated.removed) this else PersistentVector32(updated.root, updated.shift)
  }

  override fun builder(): PersistentLongMapBuilder<V> = Builder(this)

  private class Node(val slots: Array<Any?>, val owner: Any? = null)

  private data class RootState(val root: Node?, val shift: Int)

  private data class Removal(val root: Node?, val shift: Int, val removed: Boolean)

  private data class NodeRemoval(val node: Node?, val removed: Boolean)

  private class Builder<V : Any>(private val source: PersistentVector32<V>) : PersistentLongMapBuilder<V> {
    private val owner = Any()
    private var root = source.root
    private var shift = source.shift
    private var dirty = false
    private var active = true

    override fun get(key: Long): V? {
      checkActive()
      return get(root, shift, requireNonNegativeKey(key))
    }

    override fun getUnchecked(key: Long): V? {
      checkActive()
      return get(root, shift, key)
    }

    override fun put(key: Long, value: V) {
      checkActive()
      val index = requireNonNegativeKey(key)
      val requiredShift = requiredShift(index)
      var expandedRoot = root
      var expandedShift = if (expandedRoot == null) requiredShift else shift

      while (expandedRoot != null && expandedShift < requiredShift) {
        val slots = arrayOfNulls<Any>(WIDTH)
        slots[0] = expandedRoot
        expandedRoot = Node(slots, owner)
        expandedShift += BITS
      }

      root = putNode(expandedRoot, expandedShift, index, value)
      shift = expandedShift
      dirty = true
    }

    override fun remove(key: Long) {
      checkActive()
      val index = requireNonNegativeKey(key)
      val currentRoot = root ?: return
      if (requiredShift(index) > shift) return
      val result = removeNode(currentRoot, shift, index)
      if (!result.removed) return
      val trimmed = trim(result.node, shift)
      root = trimmed.root
      shift = trimmed.shift
      dirty = true
    }

    override fun build(): PersistentLongMap<V> {
      checkActive()
      active = false
      return if (dirty) PersistentVector32(root, shift) else source
    }

    private fun putNode(node: Node?, shift: Int, index: Long, value: V): Node {
      val editable = editable(node)
      val slot = slot(index, shift)
      editable.slots[slot] = if (shift == 0) {
        value
      }
      else {
        putNode(editable.slots[slot] as? Node, shift - BITS, index, value)
      }
      return editable
    }

    private fun removeNode(node: Node, shift: Int, index: Long): NodeRemoval {
      val slot = slot(index, shift)
      val child = node.slots[slot] ?: return NodeRemoval(node, false)
      val editable = editable(node)

      if (shift == 0) {
        editable.slots[slot] = null
      }
      else {
        val result = removeNode(child as Node, shift - BITS, index)
        if (!result.removed) return NodeRemoval(node, false)
        editable.slots[slot] = result.node
      }

      return NodeRemoval(if (isEmpty(editable.slots)) null else editable, true)
    }

    private fun editable(node: Node?): Node {
      if (node?.owner === owner) return node
      return Node(node?.slots?.copyOf() ?: arrayOfNulls(WIDTH), owner)
    }

    private fun checkActive() {
      check(active) { "PersistentLongMapBuilder has already been built" }
    }
  }

  companion object {
    private const val BITS = 5
    private const val WIDTH = 1 shl BITS
    private const val MASK = WIDTH - 1

    private fun requiredShift(index: Long): Int {
      if (index == 0L) return 0
      val highestBit = Long.SIZE_BITS - 1 - java.lang.Long.numberOfLeadingZeros(index)
      return highestBit / BITS * BITS
    }

    private fun fitsInRoot(index: Long, shift: Int): Boolean {
      return shift >= Long.SIZE_BITS - BITS || index ushr (shift + BITS) == 0L
    }

    private fun slot(index: Long, shift: Int): Int = ((index ushr shift) and MASK.toLong()).toInt()

    private fun <V : Any> get(root: Node?, shift: Int, index: Long): V? {
      var node = root ?: return null
      if (!fitsInRoot(index, shift)) return null
      var currentShift = shift

      while (currentShift > 0) {
        node = node.slots[slot(index, currentShift)] as? Node ?: return null
        currentShift -= BITS
      }

      @Suppress("UNCHECKED_CAST")
      return node.slots[slot(index, 0)] as V?
    }

    private fun <V : Any> put(root: Node?, shift: Int, index: Long, value: V): RootState {
      val requiredShift = requiredShift(index)
      var expandedRoot = root
      var expandedShift = if (root == null) requiredShift else shift

      while (expandedRoot != null && expandedShift < requiredShift) {
        val slots = arrayOfNulls<Any>(WIDTH)
        slots[0] = expandedRoot
        expandedRoot = Node(slots)
        expandedShift += BITS
      }

      return RootState(putNode(expandedRoot, expandedShift, index, value), expandedShift)
    }

    private fun <V : Any> putNode(node: Node?, shift: Int, index: Long, value: V): Node {
      val slots = node?.slots?.copyOf() ?: arrayOfNulls(WIDTH)
      val slot = slot(index, shift)
      slots[slot] = if (shift == 0) {
        value
      }
      else {
        putNode(slots[slot] as? Node, shift - BITS, index, value)
      }
      return Node(slots)
    }

    private fun remove(root: Node?, shift: Int, index: Long): Removal {
      root ?: return Removal(null, shift, false)
      if (requiredShift(index) > shift) return Removal(root, shift, false)
      val result = removeNode(root, shift, index)
      if (!result.removed) return Removal(root, shift, false)
      val trimmed = trim(result.node, shift)
      return Removal(trimmed.root, trimmed.shift, true)
    }

    private fun removeNode(node: Node, shift: Int, index: Long): NodeRemoval {
      val slot = slot(index, shift)
      val child = node.slots[slot] ?: return NodeRemoval(node, false)
      val slots = node.slots.copyOf()

      if (shift == 0) {
        slots[slot] = null
      }
      else {
        val result = removeNode(child as Node, shift - BITS, index)
        if (!result.removed) return NodeRemoval(node, false)
        slots[slot] = result.node
      }

      return NodeRemoval(if (isEmpty(slots)) null else Node(slots), true)
    }

    private fun trim(root: Node?, shift: Int): RootState {
      var currentRoot = root ?: return RootState(null, 0)
      var currentShift = shift

      while (currentShift > 0) {
        var childIndex = -1
        for (index in currentRoot.slots.indices) {
          if (currentRoot.slots[index] != null) {
            if (childIndex != -1) return RootState(currentRoot, currentShift)
            childIndex = index
          }
        }
        if (childIndex != 0) return RootState(currentRoot, currentShift)
        currentRoot = currentRoot.slots[0] as Node
        currentShift -= BITS
      }

      return RootState(currentRoot, currentShift)
    }

    private fun isEmpty(slots: Array<Any?>): Boolean {
      for (value in slots) {
        if (value != null) return false
      }
      return true
    }
  }
}

/**
 * Persistent 64-way indexed vector.
 *
 * It consumes six index bits per level. For 50,000 marker IDs, lookups traverse three array levels. Updates copy
 * larger arrays than [PersistentVector32].
 */
internal class PersistentVector64<V : Any> private constructor(
  private val root: Node?,
  private val shift: Int
) : PersistentLongMap<V> {
  constructor() : this(null, 0)

  override operator fun get(key: Long): V? = getUnchecked(requireNonNegativeKey(key))

  override fun getUnchecked(key: Long): V? = get(root, shift, key)

  override fun put(key: Long, value: V): PersistentVector64<V> {
    val index = requireNonNegativeKey(key)
    val updated = put(root, shift, index, value)
    return PersistentVector64(updated.root, updated.shift)
  }

  override fun remove(key: Long): PersistentVector64<V> {
    val index = requireNonNegativeKey(key)
    val updated = remove(root, shift, index)
    return if (!updated.removed) this else PersistentVector64(updated.root, updated.shift)
  }

  override fun builder(): PersistentLongMapBuilder<V> = Builder(this)

  private class Node(val slots: Array<Any?>, val owner: Any? = null)

  private data class RootState(val root: Node?, val shift: Int)

  private data class Removal(val root: Node?, val shift: Int, val removed: Boolean)

  private data class NodeRemoval(val node: Node?, val removed: Boolean)

  private class Builder<V : Any>(private val source: PersistentVector64<V>) : PersistentLongMapBuilder<V> {
    private val owner = Any()
    private var root = source.root
    private var shift = source.shift
    private var dirty = false
    private var active = true

    override fun get(key: Long): V? {
      checkActive()
      return get(root, shift, requireNonNegativeKey(key))
    }

    override fun getUnchecked(key: Long): V? {
      checkActive()
      return get(root, shift, key)
    }

    override fun put(key: Long, value: V) {
      checkActive()
      val index = requireNonNegativeKey(key)
      val requiredShift = requiredShift(index)
      var expandedRoot = root
      var expandedShift = if (expandedRoot == null) requiredShift else shift

      while (expandedRoot != null && expandedShift < requiredShift) {
        val slots = arrayOfNulls<Any>(WIDTH)
        slots[0] = expandedRoot
        expandedRoot = Node(slots, owner)
        expandedShift += BITS
      }

      root = putNode(expandedRoot, expandedShift, index, value)
      shift = expandedShift
      dirty = true
    }

    override fun remove(key: Long) {
      checkActive()
      val index = requireNonNegativeKey(key)
      val currentRoot = root ?: return
      if (requiredShift(index) > shift) return
      val result = removeNode(currentRoot, shift, index)
      if (!result.removed) return
      val trimmed = trim(result.node, shift)
      root = trimmed.root
      shift = trimmed.shift
      dirty = true
    }

    override fun build(): PersistentLongMap<V> {
      checkActive()
      active = false
      return if (dirty) PersistentVector64(root, shift) else source
    }

    private fun putNode(node: Node?, shift: Int, index: Long, value: V): Node {
      val editable = editable(node)
      val slot = slot(index, shift)
      editable.slots[slot] = if (shift == 0) {
        value
      }
      else {
        putNode(editable.slots[slot] as? Node, shift - BITS, index, value)
      }
      return editable
    }

    private fun removeNode(node: Node, shift: Int, index: Long): NodeRemoval {
      val slot = slot(index, shift)
      val child = node.slots[slot] ?: return NodeRemoval(node, false)
      val editable = editable(node)

      if (shift == 0) {
        editable.slots[slot] = null
      }
      else {
        val result = removeNode(child as Node, shift - BITS, index)
        if (!result.removed) return NodeRemoval(node, false)
        editable.slots[slot] = result.node
      }

      return NodeRemoval(if (isEmpty(editable.slots)) null else editable, true)
    }

    private fun editable(node: Node?): Node {
      if (node?.owner === owner) return node
      return Node(node?.slots?.copyOf() ?: arrayOfNulls(WIDTH), owner)
    }

    private fun checkActive() {
      check(active) { "PersistentLongMapBuilder has already been built" }
    }
  }

  companion object {
    private const val BITS: Int = 6
    private const val WIDTH: Int = 1 shl BITS
    private const val MASK:Long = (WIDTH - 1).toLong()

    private fun requiredShift(index: Long): Int {
      if (index == 0L) return 0
      val highestBit = Long.SIZE_BITS - 1 - java.lang.Long.numberOfLeadingZeros(index)
      return highestBit / BITS * BITS
    }

    private fun fitsInRoot(index: Long, shift: Int): Boolean {
      return shift >= Long.SIZE_BITS - BITS || index ushr (shift + BITS) == 0L
    }

    private fun slot(index: Long, shift: Int): Int = ((index ushr shift) and MASK).toInt()

    private fun <V : Any> get(root: Node?, shift: Int, index: Long): V? {
      var node = root ?: return null
      if (!fitsInRoot(index, shift)) return null
      var currentShift = shift

      while (currentShift > 0) {
        node = node.slots[slot(index, currentShift)] as? Node ?: return null
        currentShift -= BITS
      }

      @Suppress("UNCHECKED_CAST")
      return node.slots[slot(index, 0)] as V?
    }

    private fun <V : Any> put(root: Node?, shift: Int, index: Long, value: V): RootState {
      val requiredShift = requiredShift(index)
      var expandedRoot = root
      var expandedShift = if (root == null) requiredShift else shift

      while (expandedRoot != null && expandedShift < requiredShift) {
        val slots = arrayOfNulls<Any>(WIDTH)
        slots[0] = expandedRoot
        expandedRoot = Node(slots)
        expandedShift += BITS
      }

      return RootState(putNode(expandedRoot, expandedShift, index, value), expandedShift)
    }

    private fun <V : Any> putNode(node: Node?, shift: Int, index: Long, value: V): Node {
      val slots = node?.slots?.copyOf() ?: arrayOfNulls(WIDTH)
      val slot = slot(index, shift)
      slots[slot] = if (shift == 0) {
        value
      }
      else {
        putNode(slots[slot] as? Node, shift - BITS, index, value)
      }
      return Node(slots)
    }

    private fun remove(root: Node?, shift: Int, index: Long): Removal {
      root ?: return Removal(null, shift, false)
      if (requiredShift(index) > shift) return Removal(root, shift, false)
      val result = removeNode(root, shift, index)
      if (!result.removed) return Removal(root, shift, false)
      val trimmed = trim(result.node, shift)
      return Removal(trimmed.root, trimmed.shift, true)
    }

    private fun removeNode(node: Node, shift: Int, index: Long): NodeRemoval {
      val slot = slot(index, shift)
      val child = node.slots[slot] ?: return NodeRemoval(node, false)
      val slots = node.slots.copyOf()

      if (shift == 0) {
        slots[slot] = null
      }
      else {
        val result = removeNode(child as Node, shift - BITS, index)
        if (!result.removed) return NodeRemoval(node, false)
        slots[slot] = result.node
      }

      return NodeRemoval(if (isEmpty(slots)) null else Node(slots), true)
    }

    private fun trim(root: Node?, shift: Int): RootState {
      var currentRoot = root ?: return RootState(null, 0)
      var currentShift = shift

      while (currentShift > 0) {
        var childIndex = -1
        for (index in currentRoot.slots.indices) {
          if (currentRoot.slots[index] != null) {
            if (childIndex != -1) return RootState(currentRoot, currentShift)
            childIndex = index
          }
        }
        if (childIndex != 0) return RootState(currentRoot, currentShift)
        currentRoot = currentRoot.slots[0] as Node
        currentShift -= BITS
      }

      return RootState(currentRoot, currentShift)
    }

    private fun isEmpty(slots: Array<Any?>): Boolean {
      for (value in slots) {
        if (value != null) return false
      }
      return true
    }
  }
}

/**
 * Persistent vector of 128-element pages.
 *
 * A lookup performs a shallow [PersistentVector32] page-table lookup followed by one array access. An update copies one
 * 128-element page and the page-table path.
 */
internal class PersistentPagedVector128<V : Any> private constructor(
  private val pages: PersistentVector32<Page<V>>
) : PersistentLongMap<V> {
  constructor() : this(PersistentVector32())

  override operator fun get(key: Long): V? = getUnchecked(requireNonNegativeKey(key))

  override fun getUnchecked(key: Long): V? {
    val page = pages.getUnchecked(key ushr PAGE_BITS) ?: return null
    @Suppress("UNCHECKED_CAST")
    return page.values[(key and PAGE_MASK).toInt()] as V?
  }

  override fun put(key: Long, value: V): PersistentPagedVector128<V> {
    val index = requireNonNegativeKey(key)
    val pageKey = index ushr PAGE_BITS
    val slot = (index and PAGE_MASK).toInt()
    val oldPage = pages[pageKey]
    val values = oldPage?.values?.copyOf() ?: arrayOfNulls(PAGE_SIZE)
    val newSize = (oldPage?.size ?: 0) + if (values[slot] == null) 1 else 0
    values[slot] = value
    return PersistentPagedVector128(pages.put(pageKey, Page(values, newSize)))
  }

  override fun remove(key: Long): PersistentPagedVector128<V> {
    val index = requireNonNegativeKey(key)
    val pageKey = index ushr PAGE_BITS
    val slot = (index and PAGE_MASK).toInt()
    val oldPage = pages[pageKey] ?: return this
    if (oldPage.values[slot] == null) return this

    if (oldPage.size == 1) return PersistentPagedVector128(pages.remove(pageKey))

    val values = oldPage.values.copyOf()
    values[slot] = null
    return PersistentPagedVector128(pages.put(pageKey, Page(values, oldPage.size - 1)))
  }

  override fun builder(): PersistentLongMapBuilder<V> = Builder(this)

  private class Page<V : Any>(val values: Array<Any?>, var size: Int, val owner: Any? = null)

  private class Builder<V : Any>(private val source: PersistentPagedVector128<V>) : PersistentLongMapBuilder<V> {
    private val owner = Any()
    private val pages = source.pages.builder()
    private var dirty = false
    private var active = true

    override fun get(key: Long): V? {
      checkActive()
      return find(requireNonNegativeKey(key))
    }

    override fun getUnchecked(key: Long): V? {
      checkActive()
      return find(key)
    }

    private fun find(key: Long): V? {
      val page = pages.getUnchecked(key ushr PAGE_BITS) ?: return null
      @Suppress("UNCHECKED_CAST")
      return page.values[(key and PAGE_MASK).toInt()] as V?
    }

    override fun put(key: Long, value: V) {
      checkActive()
      val index = requireNonNegativeKey(key)
      val pageKey = index ushr PAGE_BITS
      val slot = (index and PAGE_MASK).toInt()
      val oldPage = pages[pageKey]
      val page = editable(oldPage)
      if (page !== oldPage) pages.put(pageKey, page)
      if (page.values[slot] == null) page.size++
      page.values[slot] = value
      dirty = true
    }

    override fun remove(key: Long) {
      checkActive()
      val index = requireNonNegativeKey(key)
      val pageKey = index ushr PAGE_BITS
      val slot = (index and PAGE_MASK).toInt()
      val oldPage = pages[pageKey] ?: return
      if (oldPage.values[slot] == null) return

      if (oldPage.size == 1) {
        pages.remove(pageKey)
      }
      else {
        val page = editable(oldPage)
        if (page !== oldPage) pages.put(pageKey, page)
        page.values[slot] = null
        page.size--
      }
      dirty = true
    }

    override fun build(): PersistentLongMap<V> {
      checkActive()
      @Suppress("UNCHECKED_CAST")
      val builtPages = pages.build() as PersistentVector32<Page<V>>
      active = false
      return if (dirty) PersistentPagedVector128(builtPages) else source
    }

    private fun editable(page: Page<V>?): Page<V> {
      if (page?.owner === owner) return page
      return Page(page?.values?.copyOf() ?: arrayOfNulls(PAGE_SIZE), page?.size ?: 0, owner)
    }

    private fun checkActive() {
      check(active) { "PersistentLongMapBuilder has already been built" }
    }
  }

  companion object {
    private const val PAGE_BITS: Int = 7
    private const val PAGE_SIZE: Int = 1 shl PAGE_BITS
    private const val PAGE_MASK: Long = (PAGE_SIZE - 1).toLong()
  }
}

/**
 * Persistent vector of 256-element pages.
 *
 * Compared with [PersistentPagedVector128], it reduces page-table size and can reduce lookup depth, but each isolated
 * update copies a larger page.
 */
internal class PersistentPagedVector256<V : Any> private constructor(
  private val pages: PersistentVector32<Page<V>>
) : PersistentLongMap<V> {
  constructor() : this(PersistentVector32())

  override operator fun get(key: Long): V? = getUnchecked(requireNonNegativeKey(key))

  override fun getUnchecked(key: Long): V? {
    val page = pages.getUnchecked(key ushr PAGE_BITS) ?: return null
    @Suppress("UNCHECKED_CAST")
    return page.values[(key and PAGE_MASK).toInt()] as V?
  }

  override fun put(key: Long, value: V): PersistentPagedVector256<V> {
    val index = requireNonNegativeKey(key)
    val pageKey = index ushr PAGE_BITS
    val slot = (index and PAGE_MASK).toInt()
    val oldPage = pages[pageKey]
    val values = oldPage?.values?.copyOf() ?: arrayOfNulls(PAGE_SIZE)
    val newSize = (oldPage?.size ?: 0) + if (values[slot] == null) 1 else 0
    values[slot] = value
    return PersistentPagedVector256(pages.put(pageKey, Page(values, newSize)))
  }

  override fun remove(key: Long): PersistentPagedVector256<V> {
    val index = requireNonNegativeKey(key)
    val pageKey = index ushr PAGE_BITS
    val slot = (index and PAGE_MASK).toInt()
    val oldPage = pages[pageKey] ?: return this
    if (oldPage.values[slot] == null) return this

    if (oldPage.size == 1) return PersistentPagedVector256(pages.remove(pageKey))

    val values = oldPage.values.copyOf()
    values[slot] = null
    return PersistentPagedVector256(pages.put(pageKey, Page(values, oldPage.size - 1)))
  }

  override fun builder(): PersistentLongMapBuilder<V> = Builder(this)

  private class Page<V : Any>(val values: Array<Any?>, var size: Int, val owner: Any? = null)

  private class Builder<V : Any>(private val source: PersistentPagedVector256<V>) : PersistentLongMapBuilder<V> {
    private val owner = Any()
    private val pages = source.pages.builder()
    private var dirty = false
    private var active = true

    override fun get(key: Long): V? {
      checkActive()
      return find(requireNonNegativeKey(key))
    }

    override fun getUnchecked(key: Long): V? {
      checkActive()
      return find(key)
    }

    private fun find(key: Long): V? {
      val page = pages.getUnchecked(key ushr PAGE_BITS) ?: return null
      @Suppress("UNCHECKED_CAST")
      return page.values[(key and PAGE_MASK).toInt()] as V?
    }

    override fun put(key: Long, value: V) {
      checkActive()
      val index = requireNonNegativeKey(key)
      val pageKey = index ushr PAGE_BITS
      val slot = (index and PAGE_MASK).toInt()
      val oldPage = pages[pageKey]
      val page = editable(oldPage)
      if (page !== oldPage) pages.put(pageKey, page)
      if (page.values[slot] == null) page.size++
      page.values[slot] = value
      dirty = true
    }

    override fun remove(key: Long) {
      checkActive()
      val index = requireNonNegativeKey(key)
      val pageKey = index ushr PAGE_BITS
      val slot = (index and PAGE_MASK).toInt()
      val oldPage = pages[pageKey] ?: return
      if (oldPage.values[slot] == null) return

      if (oldPage.size == 1) {
        pages.remove(pageKey)
      }
      else {
        val page = editable(oldPage)
        if (page !== oldPage) pages.put(pageKey, page)
        page.values[slot] = null
        page.size--
      }
      dirty = true
    }

    override fun build(): PersistentLongMap<V> {
      checkActive()
      @Suppress("UNCHECKED_CAST")
      val builtPages = pages.build() as PersistentVector32<Page<V>>
      active = false
      return if (dirty) PersistentPagedVector256(builtPages) else source
    }

    private fun editable(page: Page<V>?): Page<V> {
      if (page?.owner === owner) return page
      return Page(page?.values?.copyOf() ?: arrayOfNulls(PAGE_SIZE), page?.size ?: 0, owner)
    }

    private fun checkActive() {
      check(active) { "PersistentLongMapBuilder has already been built" }
    }
  }

  companion object {
    private const val PAGE_BITS: Int = 8
    private const val PAGE_SIZE: Int = 1 shl PAGE_BITS
    private const val PAGE_MASK: Long = (PAGE_SIZE - 1).toLong()
  }
}
