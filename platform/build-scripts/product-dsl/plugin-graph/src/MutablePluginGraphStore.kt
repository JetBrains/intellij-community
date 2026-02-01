// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.platform.pluginGraph

import androidx.collection.IntList
import androidx.collection.MutableIntList
import androidx.collection.MutableIntObjectMap
import androidx.collection.MutableObjectIntMap
import androidx.collection.MutableObjectList
import androidx.collection.ObjectIntMap
import androidx.collection.mutableIntListOf
import androidx.collection.mutableIntObjectMapOf
import androidx.collection.mutableObjectIntMapOf
import androidx.collection.mutableObjectListOf

/**
 * Mutable store used during graph construction and incremental updates.
 */
class MutablePluginGraphStore(
  // Columnar node storage
  @JvmField val names: MutableObjectList<String>,
  /** Node kinds with flags packed in upper bits. */
  @JvmField val kinds: MutableIntList,

  // Sparse node properties
  @JvmField val pluginIds: MutableIntObjectMap<String>,
  @JvmField val aliases: MutableIntObjectMap<Array<String>>,

  // Edge adjacency lists with packed keys: (edgeType << 24) | nodeId → adjacentIds
  @PublishedApi internal val outEdges: MutableIntObjectMap<MutableIntList>,
  // Reverse edges are stored only for edge types with reverse queries.
  @PublishedApi internal val inEdges: MutableIntObjectMap<MutableIntList>,

  // O(1) name→nodeId index per kind: [kind][name] → nodeId
  @JvmField val nameIndex: Array<ObjectIntMap<String>>,
  @JvmField var descriptorFlagsComplete: Boolean = false,
  private val nameIndexOwned: BooleanArray = BooleanArray(nameIndex.size) { true },
) {
  constructor() : this(
    names = mutableObjectListOf(),
    kinds = mutableIntListOf(),
    pluginIds = mutableIntObjectMapOf(),
    aliases = mutableIntObjectMapOf(),
    outEdges = mutableIntObjectMapOf(),
    inEdges = mutableIntObjectMapOf(),
    nameIndex = Array(NODE_TYPE_COUNT) { mutableObjectIntMapOf() },
    descriptorFlagsComplete = false,
  )

  init {
    require(nameIndexOwned.size == nameIndex.size) {
      "nameIndexOwned size ${nameIndexOwned.size} does not match nameIndex size ${nameIndex.size}"
    }
  }

  private val nodeCount: Int
    get() = names.size

  // region Edge Traversal

  /** Get successor node IDs for an edge type */
  fun successors(edgeType: Int, nodeId: Int): IntList? = outEdges.get(packEdgeMapKey(edgeType, nodeId))

  /** Get predecessor node IDs for an edge type */
  fun predecessors(edgeType: Int, nodeId: Int): IntList? = inEdges.get(packEdgeMapKey(edgeType, nodeId))

  /** Check if a node has any incoming edges of the specified type */
  fun hasInEdge(edgeType: Int, nodeId: Int): Boolean {
    return inEdges.containsKey(packEdgeMapKey(edgeType, nodeId))
  }

  // endregion

  // region Mutation API (for builder)

  fun mutableNameIndex(kind: Int): MutableObjectIntMap<String> {
    val current = nameIndex[kind]
    if (nameIndexOwned[kind] && current is MutableObjectIntMap<String>) {
      return current
    }
    val copy = MutableObjectIntMap<String>(current.size)
    current.forEach { name, id -> copy.put(name, id) }
    nameIndex[kind] = copy
    nameIndexOwned[kind] = true
    return copy
  }

  /** Get or create successor list for an edge (used by PluginGraphBuilder) */
  fun getOrCreateSuccessors(edgeType: Int, nodeId: Int): MutableIntList {
    val key = packEdgeMapKey(edgeType, nodeId)
    return outEdges.getOrPut(key) { mutableIntListOf() }
  }

  /** Get or create predecessor list for an edge (used by PluginGraphBuilder) */
  fun getOrCreatePredecessors(edgeType: Int, nodeId: Int): MutableIntList {
    val key = packEdgeMapKey(edgeType, nodeId)
    return inEdges.getOrPut(key) { mutableIntListOf() }
  }

  /**
   * Copy edges of a specific type to another store.
   * Used by tests and generators for partial store copies.
   */
  fun copyEdgesOfTypeTo(target: MutablePluginGraphStore, edgeType: Int) {
    val prefix = edgeType shl 24
    val prefixMask = 0xFF shl 24 // Match bits 24-31
    outEdges.forEach { key, list ->
      if ((key and prefixMask) == prefix) {
        target.outEdges.set(key, copyIntList(list))
      }
    }
    inEdges.forEach { key, list ->
      if ((key and prefixMask) == prefix) {
        target.inEdges.set(key, copyIntList(list))
      }
    }
  }

  /**
   * Create deep copies of the edge maps for building a new store.
   *
   * @return Pair of (outEdges copy, inEdges copy)
   */
  fun copyEdgeMaps(): Pair<MutableIntObjectMap<MutableIntList>, MutableIntObjectMap<MutableIntList>> {
    return copyIntObjectMap(outEdges) to copyIntObjectMap(inEdges)
  }

  /** Freeze mutable edge maps into CSR arrays for query efficiency. */
  fun freeze(): PluginGraphStore {
    val out = buildCsrFromMap(outEdges, nodeCount)
    val incoming = buildCsrFromMap(inEdges, nodeCount)
    val immutableNameIndex = Array(nameIndex.size) { index -> nameIndex[index] }
    return PluginGraphStore(
      names = names,
      kinds = kinds,
      pluginIds = pluginIds,
      aliases = aliases,
      outCsr = out,
      inCsr = incoming,
      nameIndex = immutableNameIndex,
      descriptorFlagsComplete = descriptorFlagsComplete,
    )
  }

  /**
   * Create an immutable snapshot that doesn't share mutable collections with this store.
   * Use this when you need a stable graph while continuing to mutate the builder.
   */
  fun freezeSnapshot(): PluginGraphStore {
    val namesCopy = MutableObjectList<String>(names.size).also { list ->
      names.forEach { list.add(it) }
    }
    val kindsCopy = MutableIntList(kinds.size).also { list ->
      kinds.forEach { list.add(it) }
    }
    val pluginIdsCopy = MutableIntObjectMap<String>(pluginIds.size).also { map ->
      pluginIds.forEach { key, value -> map[key] = value }
    }
    val aliasesCopy = MutableIntObjectMap<Array<String>>(aliases.size).also { map ->
      aliases.forEach { key, value -> map[key] = value }
    }
    val nameIndexCopy = Array<ObjectIntMap<String>>(nameIndex.size) { index ->
      val current = nameIndex[index]
      val copy = MutableObjectIntMap<String>(current.size)
      current.forEach { name, id -> copy.put(name, id) }
      copy
    }

    val out = buildCsrFromMap(outEdges, namesCopy.size)
    val incoming = buildCsrFromMap(inEdges, namesCopy.size)
    return PluginGraphStore(
      names = namesCopy,
      kinds = kindsCopy,
      pluginIds = pluginIdsCopy,
      aliases = aliasesCopy,
      outCsr = out,
      inCsr = incoming,
      nameIndex = nameIndexCopy,
      descriptorFlagsComplete = descriptorFlagsComplete,
    )
  }

  // endregion

  /** Add a simple edge and store reverse adjacency when needed. */
  fun addEdge(edgeType: Int, sourceId: Int, targetId: Int) {
    val targets = getOrCreateSuccessors(edgeType, sourceId)
    if (!targets.contains(targetId)) {
      targets.add(targetId)
    }

    if (storesReverseEdges(edgeType)) {
      val sources = getOrCreatePredecessors(edgeType, targetId)
      if (!sources.contains(sourceId)) {
        sources.add(sourceId)
      }
    }
  }
}

private fun buildCsrFromMap(
  edges: MutableIntObjectMap<MutableIntList>,
  nodeCount: Int,
): Array<CsrEdges?> {
  var maxNodeId = -1
  edges.forEach { key, _ ->
    val nodeId = unpackEdgeNodeId(key)
    if (nodeId > maxNodeId) {
      maxNodeId = nodeId
    }
  }
  val effectiveNodeCount = maxOf(nodeCount, maxNodeId + 1)
  val counts = arrayOfNulls<IntArray>(EDGE_TYPE_COUNT)
  edges.forEach { key, list ->
    val edgeType = unpackEdgeType(key)
    val nodeId = unpackEdgeNodeId(key)
    val countsForType = counts[edgeType] ?: IntArray(effectiveNodeCount).also { counts[edgeType] = it }
    countsForType[nodeId] = list.size
  }

  val result = arrayOfNulls<CsrEdges>(EDGE_TYPE_COUNT)
  val writePositions = arrayOfNulls<IntArray>(EDGE_TYPE_COUNT)
  for (edgeType in 0 until EDGE_TYPE_COUNT) {
    val countsForType = counts[edgeType] ?: continue
    val offsets = buildOffsets(countsForType)
    val entries = IntArray(offsets[effectiveNodeCount])
    result[edgeType] = CsrEdges(offsets, entries)
    writePositions[edgeType] = offsets.copyOf()
  }

  edges.forEach { key, list ->
    val edgeType = unpackEdgeType(key)
    val nodeId = unpackEdgeNodeId(key)
    val csr = result[edgeType] ?: return@forEach
    val write = writePositions[edgeType] ?: return@forEach
    var index = write[nodeId]
    list.forEach { entry ->
      csr.entries[index++] = entry
    }
    write[nodeId] = index
  }
  return result
}

/** Deep copy a MutableIntObjectMap with MutableIntList values */
private fun copyIntObjectMap(source: MutableIntObjectMap<MutableIntList>): MutableIntObjectMap<MutableIntList> {
  val result = MutableIntObjectMap<MutableIntList>(source.size)
  source.forEach { key, value ->
    result.set(key, copyIntList(value))
  }
  return result
}

/** Deep copy a MutableIntList */
private fun copyIntList(source: MutableIntList): MutableIntList {
  val result = MutableIntList(source.size)
  result.addAll(source)
  return result
}
