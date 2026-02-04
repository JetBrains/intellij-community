// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "GrazieInspection", "GrazieStyle")

package com.intellij.platform.pluginGraph

import com.intellij.platform.plugins.parser.impl.elements.ModuleLoadingRuleValue
import java.util.concurrent.atomic.AtomicReferenceArray

/**
 * Immutable edge index for fast membership and payload lookups.
 *
 * Why this exists:
 * - The graph store uses CSR arrays optimized for traversal (compact, fast iteration).
 * - CSR entries are not guaranteed to be sorted by target id, so membership and payload lookup
 *   would be O(n) scans without a secondary index.
 *
 * What it stores:
 * - CSR-like offsets per source id.
 * - Sorted target ids for each source id.
 * - Optional payloads aligned with the sorted targets (for packed edges).
 *
 * How it is built/used:
 * - Built lazily per edge type on first query and cached in the store.
 * - `contains()` and `payload()` use binary search on the sorted targets.
 * - When backed by a derived provider, targets are computed per source and cached.
 */
@PublishedApi
internal class EdgeIndex private constructor(
  @PublishedApi internal val offsets: IntArray?,
  @PublishedApi internal val targets: IntArray?,
  @PublishedApi internal val payloads: IntArray?,
  private val perSourceCache: AtomicReferenceArray<IntArray?>?,
  private val targetsProvider: ((Int) -> IntArray)?,
  private val sortTargets: Boolean,
) {
  companion object {
    fun fromCsr(offsets: IntArray, targets: IntArray, payloads: IntArray?): EdgeIndex {
      return EdgeIndex(offsets, targets, payloads, perSourceCache = null, targetsProvider = null, sortTargets = false)
    }
  }

  fun contains(sourceId: Int, targetId: Int): Boolean {
    val localOffsets = offsets
    val localTargets = targets
    if (localOffsets != null && localTargets != null) {
      val start = offsetStart(sourceId)
      val end = offsetEnd(sourceId)
      if (start >= end) return false
      return containsInSortedArray(localTargets, start, end, targetId)
    }
    val derivedTargets = targetsFor(sourceId)
    if (derivedTargets.isEmpty()) return false
    return containsInSortedArray(derivedTargets, 0, derivedTargets.size, targetId)
  }

  fun payload(sourceId: Int, targetId: Int): Int? {
    val localTargets = targets ?: return null
    val payloadArray = payloads ?: return null
    if (offsets == null) return null
    val start = offsetStart(sourceId)
    val end = offsetEnd(sourceId)
    if (start >= end) return null
    val index = localTargets.binarySearch(targetId, start, end)
    if (index < 0) return null
    return payloadArray[index]
  }

  fun count(sourceId: Int): Int {
    val localOffsets = offsets
    val localTargets = targets
    if (localOffsets != null && localTargets != null) {
      val start = offsetStart(sourceId)
      val end = offsetEnd(sourceId)
      return (end - start).coerceAtLeast(0)
    }
    return targetsFor(sourceId).size
  }

  @PublishedApi
  internal fun targetsFor(sourceId: Int): IntArray {
    val cache = perSourceCache ?: return IntArray(0)
    if (sourceId < 0 || sourceId >= cache.length()) return IntArray(0)
    val cached = cache.get(sourceId)
    if (cached != null) {
      return cached
    }
    val provider = targetsProvider ?: return IntArray(0)
    val targets = provider(sourceId)
    if (sortTargets && targets.size > 1) {
      targets.sort()
    }
    if (!cache.compareAndSet(sourceId, null, targets)) {
      return cache.get(sourceId) ?: targets
    }
    return targets
  }

  @PublishedApi
  internal fun offsetStart(sourceId: Int): Int {
    val localOffsets = offsets ?: return 0
    if (sourceId < 0 || sourceId + 1 >= localOffsets.size) return 0
    return localOffsets[sourceId]
  }

  @PublishedApi
  internal fun offsetEnd(sourceId: Int): Int {
    val localOffsets = offsets ?: return 0
    if (sourceId < 0 || sourceId + 1 >= localOffsets.size) return 0
    return localOffsets[sourceId + 1]
  }
}

private const val LINEAR_SCAN_THRESHOLD: Int = 8

private fun containsInSortedArray(targets: IntArray, start: Int, end: Int, targetId: Int): Boolean {
  val size = end - start
  if (size <= 0) return false
  if (size <= LINEAR_SCAN_THRESHOLD) {
    for (i in start until end) {
      if (targets[i] == targetId) return true
    }
    return false
  }
  return targets.binarySearch(targetId, start, end) >= 0
}

/** Direct edge membership check (cached per store/edge type). */
fun GraphScope.containsEdge(edgeType: Int, sourceId: Int, targetId: Int): Boolean {
  return store.edgeIndex(edgeType).contains(sourceId, targetId)
}

/** Direct reverse edge membership check (cached per store/edge type). */
@Suppress("unused")
fun GraphScope.containsInEdge(edgeType: Int, targetId: Int, sourceId: Int): Boolean {
  return store.edgeIndexIn(edgeType).contains(targetId, sourceId)
}

/** Direct content edge loading mode lookup (cached per store/edge type). */
fun GraphScope.contentLoadingMode(edgeType: Int, sourceId: Int, targetId: Int): ModuleLoadingRuleValue? {
  require(isContentEdgeType(edgeType)) { "Edge $edgeType does not carry content loading mode" }
  val packed = store.edgeIndex(edgeType).payload(sourceId, targetId) ?: return null
  return packedToLoadingRule(unpackLoadingMode(packed))
}

/** Direct packed edge entry lookup (cached per store/edge type). */
@Suppress("unused")
fun GraphScope.packedEdgeEntry(edgeType: Int, sourceId: Int, targetId: Int): Int? {
  require(isPackedEdgeType(edgeType)) { "Edge $edgeType does not carry packed payload" }
  return store.edgeIndex(edgeType).payload(sourceId, targetId)
}

/** Direct edge membership check by name/kind (cached per store/edge type). */
@Suppress("unused")
fun GraphScope.containsEdge(edgeType: Int, sourceId: Int, targetName: String, targetKind: Int): Boolean {
  val targetId = store.nodeId(targetName, targetKind)
  if (targetId < 0) return false
  return store.edgeIndex(edgeType).contains(sourceId, targetId)
}
