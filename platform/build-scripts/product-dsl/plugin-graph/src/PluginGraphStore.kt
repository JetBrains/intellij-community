// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.platform.pluginGraph

import androidx.collection.IntList
import androidx.collection.IntObjectMap
import androidx.collection.MutableIntList
import androidx.collection.MutableIntObjectMap
import androidx.collection.ObjectIntMap
import androidx.collection.ObjectList
import java.util.concurrent.atomic.AtomicReferenceArray

/**
 * Low-level storage for [PluginGraph] using AndroidX primitive collections.
 *
 * This immutable store keeps:
 * - **Columnar node storage**: parallel arrays for names and kinds
 * - **Sparse node properties**: maps for pluginIds, aliases, and flags
 * - **CSR edge adjacency**: compact Compressed Sparse Row layouts per edge type
 * - **Reverse adjacency**: stored for commonly queried edge types; built lazily otherwise
 * - **Edge properties**: packed into adjacency entries (loading mode, plugin flags, target scope)
 * - **Indexes**: O(1) name→nodeId lookups per node kind
 *
 * Use [MutablePluginGraphStore] for construction, then freeze to this store
 * for read-only traversal.
 *
 * @see PluginGraph for high-level query API
 * @see MutablePluginGraphStore for construction
 * @see packEdgeMapKey for key packing format
 */
class PluginGraphStore internal constructor(
  // Columnar node storage
  @JvmField val names: ObjectList<String>,
  /** Node kinds with flags packed in upper bits. Use [kind] to extract kind, flag accessors for flags. */
  @JvmField val kinds: IntList,

  // Sparse node properties
  @JvmField val pluginIds: IntObjectMap<String>,
  @JvmField val aliases: IntObjectMap<Array<String>>,

  // CSR adjacency for immutable mode (per edge type)
  @PublishedApi internal val outCsr: Array<CsrEdges?>,
  @PublishedApi internal val inCsr: Array<CsrEdges?>,

  // O(1) name→nodeId index per kind: [kind][name] → nodeId
  @JvmField val nameIndex: Array<ObjectIntMap<String>>,
  @JvmField val descriptorFlagsComplete: Boolean,
  ) {
  private val reverseCsrCache = AtomicReferenceArray<CsrEdges?>(EDGE_TYPE_COUNT).also { cache ->
    for (edgeType in 0 until EDGE_TYPE_COUNT) {
      val existing = inCsr[edgeType]
      if (existing != null) {
        cache.set(edgeType, existing)
      }
    }
  }
  private val edgeIndexCache = AtomicReferenceArray<EdgeIndex?>(EDGE_TYPE_COUNT)
  private val edgeIndexInCache = AtomicReferenceArray<EdgeIndex?>(EDGE_TYPE_COUNT)

  @PublishedApi
  internal fun getInCsr(edgeType: Int): CsrEdges? {
    val cached = reverseCsrCache.get(edgeType)
    if (cached != null) {
      return cached
    }
    val forward = outCsr[edgeType] ?: return null
    val computed = buildReverseCsrFromOut(edgeType, forward, names.size)
    if (reverseCsrCache.compareAndSet(edgeType, null, computed)) {
      return computed
    }
    return reverseCsrCache.get(edgeType)
  }

  @PublishedApi
  internal fun edgeIndex(edgeType: Int): EdgeIndex {
    val cached = edgeIndexCache.get(edgeType)
    if (cached != null) {
      return cached
    }
    val created = buildCsrEdgeIndex(outCsr[edgeType], edgeType)
    if (edgeIndexCache.compareAndSet(edgeType, null, created)) {
      return created
    }
    return edgeIndexCache.get(edgeType)!!
  }

  @PublishedApi
  internal fun edgeIndexIn(edgeType: Int): EdgeIndex {
    val cached = edgeIndexInCache.get(edgeType)
    if (cached != null) {
      return cached
    }
    val created = buildCsrEdgeIndex(getInCsr(edgeType), edgeType)
    if (edgeIndexInCache.compareAndSet(edgeType, null, created)) {
      return created
    }
    return edgeIndexInCache.get(edgeType)!!
  }

  // region Node Lookups

  /**
   * Get a node ID by name and kind.
   * Returns -1 if not found.
   */
  fun nodeId(name: String, kind: Int): Int = nameIndex[kind].getOrDefault(name, -1)

  /**
   * Iterate over all node IDs with the specified kind.
   */
  inline fun forEachNodeId(kind: Int, action: (Int) -> Unit) {
    nameIndex[kind].forEachValue(action)
  }

  // endregion

  // region Property Accessors

  /** Get node name by ID */
  fun name(nodeId: Int): String = names[nodeId]

  /** Get node kind by ID (lower 8 bits of kinds int) */
  fun kind(nodeId: Int): Int = kinds[nodeId] and NODE_KIND_MASK

  /** Check if plugin is a test plugin */
  internal fun isTestPlugin(nodeId: Int): Boolean = (kinds[nodeId] and NODE_FLAG_IS_TEST) != 0

  /** Check if plugin is DSL-defined (auto-computed dependencies) */
  fun isDslDefined(nodeId: Int): Boolean = (kinds[nodeId] and NODE_FLAG_IS_DSL_DEFINED) != 0

  /** Get plugin ID (from <id> element) */
  fun pluginId(nodeId: Int): PluginId {
    val value = pluginIds.get(nodeId)
    requireNotNull(value) {
      val nodeName = names[nodeId]
      val nodeKind = kind(nodeId)
      "No plugin id for node $nodeId ($nodeName, kind=$nodeKind)"
    }
    return PluginId(value)
  }

  /** Get plugin ID (from <id> element), or null if missing */
  fun pluginIdOrNull(nodeId: Int): PluginId? = pluginIds.get(nodeId)?.let { PluginId(it) }

  /** Check if plugin has an ID */
  fun hasPluginId(nodeId: Int): Boolean = pluginIds.containsKey(nodeId)

  /** Get plugin aliases */
  fun aliases(nodeId: Int): Array<String>? = aliases.get(nodeId)

  /** Check if module set is self-contained */
  fun isSelfContained(nodeId: Int): Boolean = (kinds[nodeId] and NODE_FLAG_SELF_CONTAINED) != 0

  /** Check if content module is a test descriptor (._test suffix) */
  fun isTestDescriptor(nodeId: Int): Boolean = (kinds[nodeId] and NODE_FLAG_IS_TEST_DESCRIPTOR) != 0

  /** Check if content module has a descriptor on disk */
  fun hasDescriptor(nodeId: Int): Boolean = (kinds[nodeId] and NODE_FLAG_HAS_DESCRIPTOR) != 0

  // endregion

  // region Edge Traversal

  /** Get successor node IDs for an edge type */
  fun successors(edgeType: Int, nodeId: Int): IntList? {
    val csr = outCsr[edgeType] ?: return null
    if (csr.isEmpty(nodeId)) return null
    return csrToIntList(csr, nodeId)
  }

  /** Get predecessor node IDs for an edge type */
  fun predecessors(edgeType: Int, nodeId: Int): IntList? {
    val csr = getInCsr(edgeType) ?: return null
    if (csr.isEmpty(nodeId)) return null
    return csrToIntList(csr, nodeId)
  }

  /** Iterate successor node IDs for an edge type */
  inline fun forEachSuccessor(edgeType: Int, nodeId: Int, action: (Int) -> Unit) {
    val csr = outCsr[edgeType] ?: return
    val start = csr.offsets[nodeId]
    val end = csr.offsets[nodeId + 1]
    for (i in start until end) {
      action(csr.entries[i])
    }
  }

  /** Iterate predecessor node IDs for an edge type */
  inline fun forEachPredecessor(edgeType: Int, nodeId: Int, action: (Int) -> Unit) {
    val csr = getInCsr(edgeType) ?: return
    val start = csr.offsets[nodeId]
    val end = csr.offsets[nodeId + 1]
    for (i in start until end) {
      action(csr.entries[i])
    }
  }

  /** Count successors for an edge type */
  fun successorCount(edgeType: Int, nodeId: Int): Int {
    val csr = outCsr[edgeType] ?: return 0
    return csr.count(nodeId)
  }

  /** Count predecessors for an edge type */
  fun predecessorCount(edgeType: Int, nodeId: Int): Int {
    val csr = getInCsr(edgeType) ?: return 0
    return csr.count(nodeId)
  }


  private fun buildCsrEdgeIndex(csr: CsrEdges?, edgeType: Int): EdgeIndex {
    val nodeCount = names.size
    if (csr == null) {
      return EdgeIndex.fromCsr(IntArray(nodeCount + 1), IntArray(0), payloads = null)
    }
    val offsets = IntArray(nodeCount + 1)
    val total = csr.entries.size
    val targets = IntArray(total)
    val payloads = if (isPackedEdgeType(edgeType)) IntArray(total) else null
    var position = 0

    for (nodeId in 0 until nodeCount) {
      offsets[nodeId] = position
      val start = csr.offsets[nodeId]
      val end = csr.offsets[nodeId + 1]
      val size = end - start
      if (size == 0) continue

      if (payloads != null) {
        val combined = LongArray(size)
        var index = 0
        for (i in start until end) {
          val entry = csr.entries[i]
          val target = unpackNodeId(entry)
          combined[index++] = (target.toLong() shl 32) or (entry.toLong() and 0xFFFFFFFFL)
        }
        combined.sort()
        for (value in combined) {
          targets[position] = (value ushr 32).toInt()
          payloads[position] = value.toInt()
          position++
        }
      }
      else {
        val slice = IntArray(size)
        var index = 0
        for (i in start until end) {
          slice[index++] = csr.entries[i]
        }
        slice.sort()
        for (value in slice) {
          targets[position++] = value
        }
      }
    }
    offsets[nodeCount] = position
    val finalTargets = if (position == targets.size) targets else targets.copyOf(position)
    val finalPayloads = payloads?.let { if (position == it.size) it else it.copyOf(position) }
    return EdgeIndex.fromCsr(offsets, finalTargets, finalPayloads)
  }


  /**
   * Get predecessor node IDs for multiple edge types (bitmask).
   * Use [EDGE_BUNDLES_ALL], [EDGE_CONTAINS_CONTENT_ALL], etc.
   */
  inline fun predecessorsMulti(edgeMask: Int, nodeId: Int, action: (Int) -> Unit) {
    var mask = edgeMask
    while (mask != 0) {
      val edgeType = Integer.numberOfTrailingZeros(mask)
      mask = mask and (mask - 1) // Clear lowest bit
      forEachPredecessor(edgeType, nodeId, action)
    }
  }

  /** Check if a node has any incoming edges of the specified type */
  fun hasInEdge(edgeType: Int, nodeId: Int): Boolean {
    val csr = getInCsr(edgeType) ?: return false
    return !csr.isEmpty(nodeId)
  }

  /**
   * Create a mutable copy of this store for incremental updates.
   *
   * @param lazyNameIndex If true, reuse nameIndex maps and copy on first mutation.
   * @param descriptorFlagsComplete Whether to set descriptor flag completeness on the new store.
   */
  fun toMutableStore(
    lazyNameIndex: Boolean = true,
    descriptorFlagsComplete: Boolean = this.descriptorFlagsComplete,
  ): MutablePluginGraphStore {
    val namesCopy = copyNames(names)
    val kindsCopy = copyKinds(kinds)
    val pluginIdsCopy = copyPluginIds(pluginIds)
    val aliasesCopy = copyAliases(aliases)

    val nameIndexCopy: Array<ObjectIntMap<String>>
    val nameIndexOwned: BooleanArray
    if (lazyNameIndex) {
      nameIndexCopy = nameIndex.copyOf()
      nameIndexOwned = BooleanArray(nameIndexCopy.size)
    }
    else {
      nameIndexCopy = copyNameIndexDeep(nameIndex)
      nameIndexOwned = BooleanArray(nameIndexCopy.size) { true }
    }

    val (outCopy, inCopy) = copyEdgeMaps()
    return MutablePluginGraphStore(
      names = namesCopy,
      kinds = kindsCopy,
      pluginIds = pluginIdsCopy,
      aliases = aliasesCopy,
      outEdges = outCopy,
      inEdges = inCopy,
      nameIndex = nameIndexCopy,
      descriptorFlagsComplete = descriptorFlagsComplete,
      nameIndexOwned = nameIndexOwned,
    )
  }

  /**
   * Create deep copies of the edge maps for building a new store.
   * Used by [toMutableStore] when extending the store with new nodes.
   *
   * @return Pair of (outEdges copy, inEdges copy)
   */
  internal fun copyEdgeMaps(): Pair<MutableIntObjectMap<MutableIntList>, MutableIntObjectMap<MutableIntList>> {
    val outCopy = MutableIntObjectMap<MutableIntList>()
    val inCopy = MutableIntObjectMap<MutableIntList>()
    for (edgeType in 0 until EDGE_TYPE_COUNT) {
      val out = outCsr[edgeType]
      if (out != null) {
        addCsrEdgesToMap(edgeType, out, outCopy)
      }
      val incoming = inCsr[edgeType]
      if (incoming != null) {
        addCsrEdgesToMap(edgeType, incoming, inCopy)
      }
    }
    return outCopy to inCopy
  }

  // endregion
}

private fun addCsrEdgesToMap(
  edgeType: Int,
  edges: CsrEdges,
  target: MutableIntObjectMap<MutableIntList>?,
) {
  if (target == null) return
  val offsets = edges.offsets
  for (nodeId in 0 until offsets.size - 1) {
    val start = offsets[nodeId]
    val end = offsets[nodeId + 1]
    if (start == end) continue
    val list = MutableIntList(end - start)
    for (index in start until end) {
      list.add(edges.entries[index])
    }
    target.set(packEdgeMapKey(edgeType, nodeId), list)
  }
}

private fun csrToIntList(edges: CsrEdges, nodeId: Int): IntList? {
  val start = edges.offsets[nodeId]
  val end = edges.offsets[nodeId + 1]
  if (start == end) return null
  val list = MutableIntList(end - start)
  for (index in start until end) {
    list.add(edges.entries[index])
  }
  return list
}

internal fun buildOffsets(counts: IntArray): IntArray {
  val offsets = IntArray(counts.size + 1)
  var total = 0
  for (nodeId in counts.indices) {
    offsets[nodeId] = total
    total += counts[nodeId]
  }
  offsets[counts.size] = total
  return offsets
}

private fun buildReverseCsrFromOut(
  edgeType: Int,
  edges: CsrEdges,
  nodeCount: Int,
): CsrEdges {
  val counts = IntArray(nodeCount)
  val offsets = edges.offsets
  for (sourceId in 0 until nodeCount) {
    val start = offsets[sourceId]
    val end = offsets[sourceId + 1]
    for (i in start until end) {
      val targetId = unpackNodeId(edges.entries[i])
      counts[targetId]++
    }
  }

  val reverseOffsets = buildOffsets(counts)
  val reverseEntries = IntArray(reverseOffsets[nodeCount])
  val writePositions = reverseOffsets.copyOf()

  for (sourceId in 0 until nodeCount) {
    val start = offsets[sourceId]
    val end = offsets[sourceId + 1]
    for (i in start until end) {
      val entry = edges.entries[i]
      val targetId = unpackNodeId(entry)
      val reverseEntry = when (edgeType) {
        EDGE_TARGET_DEPENDS_ON -> packTargetDependencyEntry(sourceId, unpackTargetDependencyScope(entry))
        EDGE_PLUGIN_XML_DEPENDS_ON_PLUGIN -> packPluginDepEntry(
          sourceId,
          unpackPluginDepOptional(entry),
          unpackPluginDepFormats(entry),
          unpackPluginDepHasConfigFile(entry),
        )
        EDGE_CONTAINS_CONTENT, EDGE_CONTAINS_CONTENT_TEST, EDGE_CONTAINS_MODULE -> packEdgeEntry(sourceId, unpackLoadingMode(entry))
        else -> sourceId
      }
      reverseEntries[writePositions[targetId]++] = reverseEntry
    }
  }

  return CsrEdges(reverseOffsets, reverseEntries)
}

// CSR (Compressed Sparse Row) adjacency: offsets[nodeId]..offsets[nodeId+1] slice into entries,
// where entries store the same packed edge ints as the mutable adjacency lists.
@PublishedApi
internal class CsrEdges(
  @JvmField val offsets: IntArray,
  @JvmField val entries: IntArray,
) {
  fun count(nodeId: Int): Int = offsets[nodeId + 1] - offsets[nodeId]

  fun isEmpty(nodeId: Int): Boolean = offsets[nodeId] == offsets[nodeId + 1]
}
