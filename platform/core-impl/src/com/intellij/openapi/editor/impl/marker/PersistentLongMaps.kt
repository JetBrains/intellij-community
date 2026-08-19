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

  fun put(key: Long, value: V): PersistentLongMap<V>

  fun remove(key: Long): PersistentLongMap<V>

  companion object {
    private val EMPTY_MAP_16 = PersistentLongMap16<Any>()
    private val EMPTY_VECTOR_32 = PersistentVector32<Any>()
    private val EMPTY_VECTOR_64 = PersistentVector64<Any>()
    private val EMPTY_PAGED_VECTOR_128 = PersistentPagedVector128<Any>()
    private val EMPTY_PAGED_VECTOR_256 = PersistentPagedVector256<Any>()

    @Suppress("UNCHECKED_CAST")
    fun <V : Any> empty(implementation: PersistentLongMapImplementation): PersistentLongMap<V> = when (implementation) {
      PersistentLongMapImplementation.MAP_16 -> EMPTY_MAP_16
      PersistentLongMapImplementation.VECTOR_32 -> EMPTY_VECTOR_32
      PersistentLongMapImplementation.VECTOR_64 -> EMPTY_VECTOR_64
      PersistentLongMapImplementation.PAGED_VECTOR_128 -> EMPTY_PAGED_VECTOR_128
      PersistentLongMapImplementation.PAGED_VECTOR_256 -> EMPTY_PAGED_VECTOR_256
    } as PersistentLongMap<V>
  }
}

/** Available implementations of [PersistentLongMap]. */
internal enum class PersistentLongMapImplementation {
  MAP_16,
  VECTOR_32,
  VECTOR_64,
  PAGED_VECTOR_128,
  PAGED_VECTOR_256
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

  override operator fun get(key: Long): V? {
    requireNonNegativeKey(key)
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

  private class Branch(val children: Array<Any?>)

  private data class Removal(val branch: Branch?, val removed: Boolean)

  companion object {
    private const val BITS = 4
    private const val WIDTH = 1 shl BITS
    private const val MASK = WIDTH - 1
    private const val LEVELS = Long.SIZE_BITS / BITS

    private fun index(key: Long, depth: Int): Int {
      val shift = (LEVELS - depth - 1) * BITS
      return ((key ushr shift) and MASK.toLong()).toInt()
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
 * Persistent 32-way indexed vector.
 *
 * A key `k` is stored at index `k`. For 50,000 marker IDs, lookups traverse four array levels.
 */
internal class PersistentVector32<V : Any> private constructor(
  private val root: Node?,
  private val shift: Int
) : PersistentLongMap<V> {
  constructor() : this(null, 0)

  override operator fun get(key: Long): V? {
    val index = requireNonNegativeKey(key)
    return get(root, shift, index)
  }

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

  private class Node(val slots: Array<Any?>)

  private data class RootState(val root: Node?, val shift: Int)

  private data class Removal(val root: Node?, val shift: Int, val removed: Boolean)

  private data class NodeRemoval(val node: Node?, val removed: Boolean)

  companion object {
    private const val BITS = 5
    private const val WIDTH = 1 shl BITS
    private const val MASK = WIDTH - 1

    private fun requiredShift(index: Long): Int {
      if (index == 0L) return 0
      val highestBit = Long.SIZE_BITS - 1 - java.lang.Long.numberOfLeadingZeros(index)
      return highestBit / BITS * BITS
    }

    private fun slot(index: Long, shift: Int): Int = ((index ushr shift) and MASK.toLong()).toInt()

    private fun <V : Any> get(root: Node?, shift: Int, index: Long): V? {
      var node = root ?: return null
      if (requiredShift(index) > shift) return null
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

  override operator fun get(key: Long): V? {
    val index = requireNonNegativeKey(key)
    return get(root, shift, index)
  }

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

  private class Node(val slots: Array<Any?>)

  private data class RootState(val root: Node?, val shift: Int)

  private data class Removal(val root: Node?, val shift: Int, val removed: Boolean)

  private data class NodeRemoval(val node: Node?, val removed: Boolean)

  companion object {
    private const val BITS = 6
    private const val WIDTH = 1 shl BITS
    private const val MASK = WIDTH - 1

    private fun requiredShift(index: Long): Int {
      if (index == 0L) return 0
      val highestBit = Long.SIZE_BITS - 1 - java.lang.Long.numberOfLeadingZeros(index)
      return highestBit / BITS * BITS
    }

    private fun slot(index: Long, shift: Int): Int = ((index ushr shift) and MASK.toLong()).toInt()

    private fun <V : Any> get(root: Node?, shift: Int, index: Long): V? {
      var node = root ?: return null
      if (requiredShift(index) > shift) return null
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

  override operator fun get(key: Long): V? {
    val index = requireNonNegativeKey(key)
    val page = pages[index ushr PAGE_BITS] ?: return null
    @Suppress("UNCHECKED_CAST")
    return page.values[(index and PAGE_MASK.toLong()).toInt()] as V?
  }

  override fun put(key: Long, value: V): PersistentPagedVector128<V> {
    val index = requireNonNegativeKey(key)
    val pageKey = index ushr PAGE_BITS
    val slot = (index and PAGE_MASK.toLong()).toInt()
    val oldPage = pages[pageKey]
    val values = oldPage?.values?.copyOf() ?: arrayOfNulls(PAGE_SIZE)
    val newSize = (oldPage?.size ?: 0) + if (values[slot] == null) 1 else 0
    values[slot] = value
    return PersistentPagedVector128(pages.put(pageKey, Page(values, newSize)))
  }

  override fun remove(key: Long): PersistentPagedVector128<V> {
    val index = requireNonNegativeKey(key)
    val pageKey = index ushr PAGE_BITS
    val slot = (index and PAGE_MASK.toLong()).toInt()
    val oldPage = pages[pageKey] ?: return this
    if (oldPage.values[slot] == null) return this

    if (oldPage.size == 1) return PersistentPagedVector128(pages.remove(pageKey))

    val values = oldPage.values.copyOf()
    values[slot] = null
    return PersistentPagedVector128(pages.put(pageKey, Page(values, oldPage.size - 1)))
  }

  private class Page<V : Any>(val values: Array<Any?>, val size: Int)

  companion object {
    private const val PAGE_BITS = 7
    private const val PAGE_SIZE = 1 shl PAGE_BITS
    private const val PAGE_MASK = PAGE_SIZE - 1
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

  override operator fun get(key: Long): V? {
    val index = requireNonNegativeKey(key)
    val page = pages[index ushr PAGE_BITS] ?: return null
    @Suppress("UNCHECKED_CAST")
    return page.values[(index and PAGE_MASK.toLong()).toInt()] as V?
  }

  override fun put(key: Long, value: V): PersistentPagedVector256<V> {
    val index = requireNonNegativeKey(key)
    val pageKey = index ushr PAGE_BITS
    val slot = (index and PAGE_MASK.toLong()).toInt()
    val oldPage = pages[pageKey]
    val values = oldPage?.values?.copyOf() ?: arrayOfNulls(PAGE_SIZE)
    val newSize = (oldPage?.size ?: 0) + if (values[slot] == null) 1 else 0
    values[slot] = value
    return PersistentPagedVector256(pages.put(pageKey, Page(values, newSize)))
  }

  override fun remove(key: Long): PersistentPagedVector256<V> {
    val index = requireNonNegativeKey(key)
    val pageKey = index ushr PAGE_BITS
    val slot = (index and PAGE_MASK.toLong()).toInt()
    val oldPage = pages[pageKey] ?: return this
    if (oldPage.values[slot] == null) return this

    if (oldPage.size == 1) return PersistentPagedVector256(pages.remove(pageKey))

    val values = oldPage.values.copyOf()
    values[slot] = null
    return PersistentPagedVector256(pages.put(pageKey, Page(values, oldPage.size - 1)))
  }

  private class Page<V : Any>(val values: Array<Any?>, val size: Int)

  companion object {
    private const val PAGE_BITS = 8
    private const val PAGE_SIZE = 1 shl PAGE_BITS
    private const val PAGE_MASK = PAGE_SIZE - 1
  }
}
