// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import com.intellij.openapi.editor.ex.DocumentPatch
import com.intellij.openapi.editor.ex.DocumentText
import com.intellij.openapi.editor.impl.marker.PMarkerRoot.MarkerDeltaProcessor
import com.intellij.openapi.editor.impl.marker.PMarkerRoot.MarkerEntry
import com.intellij.openapi.editor.impl.marker.PMarkerRootImpl.Companion.NULL_NODE
import com.intellij.openapi.util.TextRangeScalarUtil
import com.intellij.util.Processor
import com.intellij.util.containers.ConcurrentLongLongMap
import com.intellij.util.containers.Java11Shim
import org.jetbrains.annotations.ApiStatus
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater
import java.util.function.Consumer

/**
 * Immutable persistent marker root backed by an AVL tree keyed by start offset and handle order.
 *
 * Handle order values are stable node keys. [states] is a persistent radix map, so different roots can store
 * different node contents for the same handle. Each valid node stores its own parent ID, which allows a marker to be
 * resolved by its handle even when an ancestor subtree carries an unpushed lazy offset delta.
 *
 * Equal start offsets are supported. If an edit collapses differently ordered starts to one offset and therefore
 * violates handle tie ordering, the affected middle part is sorted before it is rebuilt as a balanced AVL tree.
 */
open class PMarkerRootImpl private constructor(
  private val rootId: Long,
  private val states: PersistentLongMap<StoredNode>,
  /** Number of valid markers that use a persistent policy in the entire tree represented by this root. */
  private val persistentMarkerCount: Int,
  /** Maps marker IDs to absolute ranges. Roots share this cache until the document changes. */
  @Volatile
  private var cachedOffsets: ConcurrentLongLongMap? = null,
  /**
   * Identifies roots with identical marker validity and offsets. [updateSpec] preserves this token.
   * The token does not retain the root or its offset cache.
   */
  override val resolutionCacheIdentity: Any = Any(),
  /**
   * Describes one direct text edit. Null for moves, full traversals, and changes to the marker structure.
   */
  private val resolutionUpdate: PMarkerResolutionUpdate? = null,
) : PMarkerRoot {
  /**
   * Advances a valid cached resolution across one direct edit without traversing the tree.
   * Reuses unchanged offsets before the affected area and adds the edit delta after it.
   * Returns [PMarkerResolution.UNRESOLVED] when the source identity differs or edit metadata is unavailable.
   * Ranges that touch the affected area also require normal resolution.
   * Invalid and absent resolutions also require normal resolution.
   *
   * @param previousIdentity The root identity associated with [resolution].
   * @param resolution The cached resolution before the edit.
   */
  override fun advanceResolution(previousIdentity: Any, resolution: Long): Long =
    resolutionUpdate?.advance(previousIdentity, resolution) ?: PMarkerResolution.UNRESOLVED

  override fun <T : SnapshotRangeMarkerImpl> createHandle(marker: T, retainStrong: Boolean): SnapshotMarkerReference<T?> =
    if (retainStrong) StrongSnapshotMarkerReference(marker) else WeakSnapshotMarkerReference(marker)

  override fun <T : SnapshotRangeMarkerImpl> createStrongHandle(marker: T): SnapshotMarkerReference<T> =
    StrongSnapshotMarkerReference(marker)

  override fun emptyRoot(): PMarkerRootImpl = empty()

  /** Restores marker offsets without sharing the old offset cache. */
  override fun restoreEntries(entries: List<MarkerEntry>): PMarkerRoot =
    if (entries.isEmpty()) this
    else PMarkerRootImpl(rootId, states, persistentMarkerCount).purgeMany(entries.map { it.handle }).insertMany(entries)

  override fun isEmpty(): Boolean = rootId == NULL_NODE

  override fun isValid(handle: SnapshotMarkerReference<*>): Boolean = states.getUnchecked(handle.order) is ValidNode

  override fun resolve(handle: SnapshotMarkerReference<*>): Long {
    val markerId = handle.order
    return when (val state = states.getUnchecked(markerId)) {
      null, AbsentNode -> PMarkerResolution.ABSENT
      is InvalidNode -> PMarkerResolution.invalid(state.range)
      is ValidNode -> absoluteOffsets(state, markerId)
    }
  }

  override fun invalidationReason(handle: SnapshotMarkerReference<*>): String? = (states.getUnchecked(handle.order) as? InvalidNode)?.reason

  override fun insert(
    handle: SnapshotMarkerReference<*>,
    startOffset: Int,
    endOffset: Int,
    spec: Byte,
    flavorFlags: Byte,
    measure: Int,
    policy: MarkerPolicy,
  ): PMarkerRoot {
    val editor = MapBatchEditor(states, persistentMarkerCount)
    val entry = MarkerEntry(handle, startOffset, endOffset, spec, flavorFlags, measure, policy)
    val newRoot = editor.insert(rootId, entry)
    val cache = cachedOffsets
    if (cache != null) cacheInsertedOffsets(cache, entry)
    return PMarkerRootImpl(newRoot, editor.build(), editor.persistentMarkerCount(), cache)
  }

  override fun insertMany(entries: List<MarkerEntry>): PMarkerRoot {
    if (entries.isEmpty()) return this
    val editor = MapBatchEditor(states, persistentMarkerCount)
    var newRoot = rootId
    for (entry in entries) {
      newRoot = editor.insert(newRoot, entry)
    }
    val cache = cachedOffsets
    if (cache != null) {
      for (entry in entries) cacheInsertedOffsets(cache, entry)
    }
    return PMarkerRootImpl(newRoot, editor.build(), editor.persistentMarkerCount(), cache)
  }

  override fun updateFlavor(handle: SnapshotMarkerReference<*>, flavorFlags: Byte): PMarkerRoot {
    val markerId = handle.order
    val state = states.getUnchecked(markerId) as? ValidNode ?: return this
    if (state.entry.flavorFlags == flavorFlags) return this

    val editor = MapBatchEditor(states, persistentMarkerCount)
    editor.putValid(markerId, state.copy(entry = state.entry.copy(flavorFlags = flavorFlags)))
    var currentId = markerId
    while (currentId != NULL_NODE) {
      val node = editor.valid(currentId)
      val updatedFlavorFlags = subtreeFlavorFlags(editor, node.entry, node.leftId, node.rightId)
      if (updatedFlavorFlags != node.subtreeFlavorFlags) {
        editor.putValid(currentId, node.copy(subtreeFlavorFlags = updatedFlavorFlags))
      }
      currentId = node.parentId
    }
    return PMarkerRootImpl(rootId, editor.build(), persistentMarkerCount, cachedOffsets, resolutionCacheIdentity, resolutionUpdate)
  }

  override fun updateSpec(handle: SnapshotMarkerReference<*>, spec: Byte): PMarkerRoot {
    val markerId = handle.order
    val state = states.getUnchecked(markerId) as? ValidNode ?: return this
    return PMarkerRootImpl(
      rootId,
      states.put(markerId, state.copy(entry = state.entry.copy(spec = spec))),
      persistentMarkerCount,
      cachedOffsets,
      resolutionCacheIdentity,
      resolutionUpdate,
    )
  }

  override fun updateMeasure(handle: SnapshotMarkerReference<*>, measure: Int): PMarkerRoot {
    val markerId = handle.order
    val state = states.getUnchecked(markerId) as? ValidNode ?: return this
    if (state.entry.measure == measure) return this

    val editor = MapBatchEditor(states, persistentMarkerCount)
    editor.putValid(markerId, state.copy(entry = state.entry.copy(measure = measure)))
    var currentId = markerId
    while (currentId != NULL_NODE) {
      val node = editor.valid(currentId)
      val updatedAggregate = subtreeAggregate(editor, node.entry, node.leftId, node.rightId)
      if (updatedAggregate != node.subtreeAggregate) {
        editor.putValid(currentId, node.copy(subtreeAggregate = updatedAggregate))
      }
      currentId = node.parentId
    }
    return PMarkerRootImpl(rootId, editor.build(), persistentMarkerCount, cachedOffsets, resolutionCacheIdentity, resolutionUpdate)
  }

  override fun remove(handle: SnapshotMarkerReference<*>): PMarkerRoot {
    val markerId = handle.order
    return when (val state = states.getUnchecked(markerId)) {
      null -> this
      AbsentNode -> this
      is InvalidNode -> PMarkerRootImpl(
        rootId,
        states.put(markerId, AbsentNode),
        persistentMarkerCount,
        cachedOffsets,
      )
      is ValidNode -> {
        val offsets = absoluteOffsets(state, markerId)
        val startOffset = TextRangeScalarUtil.startOffset(offsets)
        val key = PositionKey(startOffset, markerId)
        val editor = MapBatchEditor(states, persistentMarkerCount)
        val newRoot = removeByKey(editor, rootId, key)
        editor.putAbsent(markerId)
        editor.setParent(newRoot, NULL_NODE)
        PMarkerRootImpl(
          newRoot,
          editor.build(),
          decrementPersistentMarkerCount(persistentMarkerCount, state.entry.policy),
          cachedOffsets,
        )
      }
    }
  }

  override fun removeMany(handles: Collection<SnapshotMarkerReference<*>>): PMarkerRoot = removeMarkers(handles, retainTombstones = true)

  override fun purgeMany(handles: Collection<SnapshotMarkerReference<*>>): PMarkerRoot = removeMarkers(handles, retainTombstones = false)

  private fun removeMarkers(handles: Collection<SnapshotMarkerReference<*>>, retainTombstones: Boolean): PMarkerRoot {
    if (handles.isEmpty()) return this
    val editor = MapBatchEditor(states, persistentMarkerCount)
    var newRoot = rootId
    for (handle in handles) {
      val markerId = handle.order
      when (val state = editor.state(markerId)) {
        null -> Unit
        AbsentNode -> if (!retainTombstones) editor.remove(markerId)
        is InvalidNode -> {
          if (retainTombstones) {
            editor.putAbsent(markerId)
          }
          else {
            editor.remove(markerId)
          }
        }
        is ValidNode -> {
          val offsetDelta = editor.ancestorDelta(state)
          val startOffset = state.entry.nodeStart + offsetDelta
          newRoot = removeByKey(editor, newRoot, PositionKey(startOffset, markerId))
          if (retainTombstones) {
            editor.putAbsent(markerId)
          }
          else {
            editor.remove(markerId)
          }
          editor.removePolicy(state.entry.policy)
          editor.setParent(newRoot, NULL_NODE)
        }
      }
    }
    val newStates = editor.build()
    return if (newStates === states) this else PMarkerRootImpl(newRoot, newStates, editor.persistentMarkerCount(), cachedOffsets)
  }

  override fun containsMarker(handle: SnapshotMarkerReference<*>): Boolean = states.getUnchecked(handle.order) != null

  override fun applyPatch(
    patch: DocumentPatch,
    beforeText: DocumentText,
    afterText: DocumentText,
    invalidatedMarkerConsumer: Consumer<in SnapshotMarkerReference<*>>,
    affectedMarkerConsumer: Consumer<in SnapshotMarkerReference<*>>,
  ): PMarkerRoot {
    validatePatch(patch, beforeText, afterText)
    val editStart = patch.startOffset()
    val editEnd = patch.endOffset()
    val oldLength = editEnd - editStart
    val newLength = patch.newFragment().length
    if (oldLength == 0 && newLength == 0 || rootId == NULL_NODE) return this
    if (persistentMarkerCount > 0 && PersistentMarkerPolicy.requiresFullTraversal(patch, beforeText, afterText)) {
      return applyPatchWithFullTraversal(patch, beforeText, afterText, invalidatedMarkerConsumer, affectedMarkerConsumer)
    }

    val delta = newLength - oldLength
    val editor = MapBatchEditor(states, persistentMarkerCount)

    val [before, fromEditStart] = splitByStart(editor, rootId, editStart, equalGoesLeft = false)
    val [middle, after] = splitByStart(editor, fromEditStart, editEnd, equalGoesLeft = true)

    val updatedBefore = updateMarkersStartingBeforeEdit(
      editor, before, patch, beforeText, afterText, invalidatedMarkerConsumer, affectedMarkerConsumer
    )
    val middleEntries = ArrayList<MarkerEntry>()
    collectEntries(editor, middle, 0, middleEntries)

    val transformedMiddle = ArrayList<MarkerEntry>(middleEntries.size)
    for (entry in middleEntries) {
      val result = transform(editor, entry, patch, beforeText, afterText, invalidatedMarkerConsumer, affectedMarkerConsumer)
      if (result.isValid) {
        transformedMiddle.add(result.applyTo(entry))
      }
      else {
        editor.putInvalid(entry, result.errorReason!!)
      }
    }

    if (!isSortedByPosition(transformedMiddle)) transformedMiddle.sortWith(ENTRY_COMPARATOR)

    val rebuiltMiddle = buildBalanced(editor, transformedMiddle)
    shift(editor, after, delta)

    var newRoot = joinDisjoint(editor, updatedBefore, rebuiltMiddle)
    newRoot = joinDisjoint(editor, newRoot, after)
    if (oldLength == 0 && patch.moveOffset() != editStart) {
      // The regular insertion update has already shifted the source range. Retarget its contained markers to the
      // inserted copy before the deletion half of moveText is applied.
      val moveStart = patch.moveOffset()
      val moveEnd = moveStart + newLength
      newRoot = retargetContainedMarkers(
        editor, newRoot, moveStart, moveEnd, editStart - moveStart, afterText, invalidatedMarkerConsumer, affectedMarkerConsumer
      )
    }
    editor.setParent(newRoot, NULL_NODE)
    val resolutionUpdate = if (patch.moveOffset() == editStart) {
      PMarkerResolutionUpdate(resolutionCacheIdentity, minOf(editStart, editor.firstChangedEnd), editEnd, delta)
    }
    else {
      null
    }
    return PMarkerRootImpl(newRoot, editor.build(), editor.persistentMarkerCount(), resolutionUpdate = resolutionUpdate)
  }

  private fun applyPatchWithFullTraversal(
    patch: DocumentPatch,
    beforeText: DocumentText,
    afterText: DocumentText,
    invalidatedMarkerConsumer: Consumer<in SnapshotMarkerReference<*>>,
    affectedMarkerConsumer: Consumer<in SnapshotMarkerReference<*>>,
  ): PMarkerRoot {
    val editor = MapBatchEditor(states, persistentMarkerCount)
    val entries = ArrayList<MarkerEntry>()
    collectEntries(editor, rootId, 0, entries)

    val transformedEntries = ArrayList<MarkerEntry>(entries.size)
    for (entry in entries) {
      val result = transform(editor, entry, patch, beforeText, afterText, invalidatedMarkerConsumer, affectedMarkerConsumer)
      if (result.isValid) {
        transformedEntries.add(result.applyTo(entry))
      }
      else {
        editor.putInvalid(entry, result.errorReason!!)
      }
    }
    if (!isSortedByPosition(transformedEntries)) transformedEntries.sortWith(ENTRY_COMPARATOR)

    var newRoot = buildBalanced(editor, transformedEntries)
    val oldLength = patch.endOffset() - patch.startOffset()
    if (oldLength == 0 && patch.moveOffset() != patch.startOffset()) {
      val moveStart = patch.moveOffset()
      val moveEnd = moveStart + patch.newFragment().length
      newRoot = retargetContainedMarkers(
        editor, newRoot, moveStart, moveEnd, patch.startOffset() - moveStart, afterText,
        invalidatedMarkerConsumer, affectedMarkerConsumer
      )
    }
    editor.setParent(newRoot, NULL_NODE)
    return PMarkerRootImpl(newRoot, editor.build(), editor.persistentMarkerCount())
  }

  /**
   * Keeps this branch's marker states and adds valid markers that exist only in [other].
   *
   * Invalid or removed markers that exist only in [other] remain absent, which preserves their invalidity while this
   * branch stays authoritative for every marker ID it has already observed.
   */
  override fun mergeValidMarkersFrom(other: PMarkerRoot): PMarkerRootImpl {
    other as PMarkerRootImpl
    if (other.rootId == NULL_NODE) return this

    val otherEntries = ArrayList<MarkerEntry>()
    collectEntries(MapBatchEditor(other.states, other.persistentMarkerCount), other.rootId, 0, otherEntries)
    val missingEntries = otherEntries.filter { states.getUnchecked(it.handle.order) == null }
    if (missingEntries.isEmpty()) return this

    val editor = MapBatchEditor(states, persistentMarkerCount)
    var mergedRootId = rootId
    for (entry in missingEntries) {
      editor.putValid(
        entry.handle.order,
        ValidNode(
          entry = entry,
          parentId = NULL_NODE,
          leftId = NULL_NODE,
          rightId = NULL_NODE,
          height = 1,
          maximumEndOffset = entry.nodeEnd,
          lazyOffsetDelta = 0,
          subtreeFlavorFlags = entry.flavorFlags,
          subtreeAggregate = entry.measure,
        )
      )
      editor.addPolicy(entry.policy)
      mergedRootId = insertAvl(editor, mergedRootId, entry.handle.order)
    }
    editor.setParent(mergedRootId, NULL_NODE)
    return PMarkerRootImpl(mergedRootId, editor.build(), editor.persistentMarkerCount())
  }

  /**
   * Reports valid marker entries that non-strictly intersect `[startOffset, endOffset]`.
   *
   * This implementation uses a start-ordered tree augmented with subtree maximum ends. It is output-sensitive in
   * typical cases, but unlike a priority-search tree it does not guarantee worst-case `O(log n + k)` reporting.
   */
  override fun processRangeMarkersOverlappingWith(
    startOffset: Int,
    endOffset: Int,
    tastePreference: Int,
    processor: Processor<in MarkerEntry>,
  ): Boolean {
    require(startOffset >= 0) { "startOffset must be non-negative but got: $startOffset" }
    require(endOffset >= startOffset) { "endOffset must not precede startOffset, but got: ($startOffset, $endOffset)" }
    return processRangeMarkersOverlappingWith(
      rootId,
      ancestorDelta = 0,
      queryStart = startOffset,
      queryEnd = endOffset,
      requiredFlavorFlags = tastePreference and ALL_FLAVOR_FLAGS,
      processor = processor,
    )
  }

  override fun getPrefixAggregate(offset: Int): Int {
    if (rootId == NULL_NODE) return 0
    val root = states.getUnchecked(rootId) as ValidNode
    if (offset >= root.maximumEndOffset) return root.subtreeAggregate

    var prefixAggregate = 0
    var nodeId = rootId
    var ancestorDelta = 0
    while (nodeId != NULL_NODE) {
      val node = states.getUnchecked(nodeId) as ValidNode
      val startOffset = node.entry.nodeStart + ancestorDelta
      val childDelta = ancestorDelta + node.lazyOffsetDelta
      if (startOffset <= offset) {
        prefixAggregate += subtreeAggregate(node.leftId) + node.entry.measure
        nodeId = node.rightId
      }
      else {
        nodeId = node.leftId
      }
      ancestorDelta = childDelta
    }
    return prefixAggregate
  }

  override fun overlappingIterator(startOffset: Int, endOffset: Int, tastePreference: Int): Iterator<MarkerEntry> {
    require(startOffset >= 0) { "startOffset must be non-negative but got: $startOffset" }
    require(endOffset >= startOffset) { "endOffset must not precede startOffset, but got: ($startOffset, $endOffset)" }
    val requiredFlavorFlags = tastePreference and ALL_FLAVOR_FLAGS

    return object : Iterator<MarkerEntry> {
      private val stack = ArrayDeque<TraversalFrame>()
      private var nextEntry: MarkerEntry? = null

      init {
        pushLeft(rootId, initialAncestorDelta = 0)
      }

      override fun hasNext(): Boolean {
        if (nextEntry == null) nextEntry = findNext()
        return nextEntry != null
      }

      override fun next(): MarkerEntry {
        if (!hasNext()) throw NoSuchElementException()
        val result = nextEntry!!
        nextEntry = null
        return result
      }

      private fun findNext(): MarkerEntry? {
        while (stack.isNotEmpty()) {
          val (node, ancestorDelta) = stack.removeLast()
          val childDelta = ancestorDelta + node.lazyOffsetDelta
          pushLeft(node.rightId, childDelta)

          val entry = node.entry
          val end = entry.nodeEnd + ancestorDelta
          if (end >= startOffset && containsAllFlavorFlags(entry.flavorFlags, requiredFlavorFlags)) {
            val start = entry.nodeStart + ancestorDelta
            return if (ancestorDelta == 0) entry else entry.copy(nodeStart = start, nodeEnd = end)
          }
        }
        return null
      }

      private fun pushLeft(initialNodeId: Long, initialAncestorDelta: Int) {
        var nodeId = initialNodeId
        var ancestorDelta = initialAncestorDelta
        while (nodeId != NULL_NODE) {
          val node = states.getUnchecked(nodeId) as ValidNode
          if (!containsAllFlavorFlags(node.subtreeFlavorFlags, requiredFlavorFlags) ||
              node.maximumEndOffset + ancestorDelta < startOffset) {
            return
          }

          val childDelta = ancestorDelta + node.lazyOffsetDelta
          if (node.entry.nodeStart + ancestorDelta <= endOffset) {
            stack.addLast(TraversalFrame(node, ancestorDelta))
          }
          nodeId = node.leftId
          ancestorDelta = childDelta
        }
      }
    }
  }

  @ApiStatus.Internal
  override fun processAllRangeMarkers(processor: MarkerDeltaProcessor): Boolean {
    return processAllRangeMarkers(rootId, ancestorDelta = 0, processor)
  }

  private fun processAllRangeMarkers(nodeId: Long, ancestorDelta: Int, processor: MarkerDeltaProcessor): Boolean {
    if (nodeId == NULL_NODE) return true
    val node = states.getUnchecked(nodeId) as ValidNode
    val childDelta = ancestorDelta + node.lazyOffsetDelta
    return processAllRangeMarkers(node.leftId, childDelta, processor) &&
           processor.process(node.entry, ancestorDelta) &&
           processAllRangeMarkers(node.rightId, childDelta, processor)
  }

  private val maximumHeight: Int = guessMaximumHeight()
  private fun absoluteOffsets(state: ValidNode, markerId: Long): Long {
    var cachedOffsets = cachedOffsets
    if (cachedOffsets == null) {
      cachedOffsets = Java11Shim.createConcurrentLongLongMap(UNCACHED_OFFSETS)
      if (!CACHED_OFFSETS_UPDATER.compareAndSet(this, null, cachedOffsets)) {
        cachedOffsets = this.cachedOffsets
      }
    }
    val cached = cachedOffsets!![markerId]
    if (cached != UNCACHED_OFFSETS) {
      return cached
    }

    val path = arrayOfNulls<ValidNode>(maximumHeight)
    var i = 0
    var node = state
    var nodeId = markerId
    var result: Int
    while (true) {
      val parentId = node.parentId
      if (parentId == NULL_NODE) {
        result = 0
        cachedOffsets.putIfAbsent(nodeId, TextRangeScalarUtil.toScalarRange(node.entry.nodeStart, node.entry.nodeEnd))
        break
      }

      path[i] = node
      i++
      node = states.getUnchecked(parentId) as? ValidNode
             ?: throw IllegalStateException("Parent $parentId is not a valid marker node")
      nodeId = parentId
      val cached = cachedOffsets[nodeId]
      if (cached != UNCACHED_OFFSETS) {
        result = TextRangeScalarUtil.startOffset(cached) - node.entry.nodeStart
        break
      }
    }

    while (i > 0) {
      result += node.lazyOffsetDelta
      i--
      node = path[i]!!
      cachedOffsets.putIfAbsent(node.entry.handle.order,
                                TextRangeScalarUtil.toScalarRange(node.entry.nodeStart + result, node.entry.nodeEnd + result))
    }
    return TextRangeScalarUtil.toScalarRange(state.entry.nodeStart + result, state.entry.nodeEnd + result)
  }

  private fun guessMaximumHeight(): Int {
    // maximum AVL tree height is 1.44*log2(size)+1
    val size = states.size()
    return if (size == 0) 1 else ((23 * (31 - Integer.numberOfLeadingZeros(size))) shr 4) + 3
  }

  private fun subtreeAggregate(markerId: Long): Int =
    if (markerId == NULL_NODE) 0 else (states.getUnchecked(markerId) as ValidNode).subtreeAggregate

  private fun processRangeMarkersOverlappingWith(
    nodeId: Long,
    ancestorDelta: Int,
    queryStart: Int,
    queryEnd: Int,
    requiredFlavorFlags: Int,
    processor: Processor<in MarkerEntry>,
  ): Boolean {
    if (nodeId == NULL_NODE) {
      return true
    }
    val node = states.getUnchecked(nodeId) as ValidNode
    if (!containsAllFlavorFlags(node.subtreeFlavorFlags, requiredFlavorFlags)) {
      return true
    }
    if (node.maximumEndOffset + ancestorDelta < queryStart) {
      return true
    }

    val childDelta = ancestorDelta + node.lazyOffsetDelta
    if (!processRangeMarkersOverlappingWith(
        node.leftId,
        childDelta,
        queryStart,
        queryEnd,
        requiredFlavorFlags,
        processor,
      )) {
      return false
    }

    val start = node.entry.nodeStart + ancestorDelta
    val end = node.entry.nodeEnd + ancestorDelta
    if (start <= queryEnd && end >= queryStart && containsAllFlavorFlags(node.entry.flavorFlags, requiredFlavorFlags)) {
      if (!processor.process(node.entry)) {
        return false
      }
    }
    if (start <= queryEnd) {
      if (!processRangeMarkersOverlappingWith(
          node.rightId,
          childDelta,
          queryStart,
          queryEnd,
          requiredFlavorFlags,
          processor,
        )) {
        return false
      }
    }
    return true
  }

  private sealed interface StoredNode

  private data class TraversalFrame(val node: ValidNode, val ancestorDelta: Int)

  /**
   * Stores one valid [entry] and the AVL links [parentId], [leftId], and [rightId].
   *
   * It caches [height], [maximumEndOffset], [subtreeFlavorFlags], and [subtreeAggregate].
   * The offsets in [entry] and [maximumEndOffset] do not include pending shifts from ancestors.
   * Readers add those shifts during traversal.
   * [lazyOffsetDelta] is already included in both values and applies to the child subtrees.
   */
  private data class ValidNode(
    /** Stores the marker state at this node. */
    val entry: MarkerEntry,

    /** Identifies the parent node, or [NULL_NODE] for the tree root. */
    val parentId: Long,

    /** Identifies the left child, or [NULL_NODE] when no left child exists. */
    val leftId: Long,

    /** Identifies the right child, or [NULL_NODE] when no right child exists. */
    val rightId: Long,

    /** Stores the AVL subtree height, including this node. */
    val height: Int,

    /** Stores the maximum end offset in this subtree, excluding deltas from ancestors. */
    val maximumEndOffset: Int,

    /** Stores the offset delta that readers must add to both child subtrees. */
    val lazyOffsetDelta: Int,

    /** Stores the bitwise OR of all flavor flags in this subtree. */
    val subtreeFlavorFlags: Byte,

    /** Stores the sum of all [MarkerEntry.measure]s in this subtree. */
    val subtreeAggregate: Int,
  ) : StoredNode

  private data class InvalidNode(
    val reason: String,
    val range: Long,
  ) : StoredNode

  private object AbsentNode : StoredNode

  private data class PositionKey(val startOffset: Int, val markerId: Long) : Comparable<PositionKey> {
    constructor(entry: MarkerEntry) : this(entry.nodeStart, entry.handle.order)

    override fun compareTo(other: PositionKey): Int {
      val byOffset = startOffset.compareTo(other.startOffset)
      return if (byOffset != 0) byOffset else markerId.compareTo(other.markerId)
    }
  }

  private data class ExtractMinimumResult(val rootId: Long, val minimumId: Long)

  private class MapBatchEditor(
    states: PersistentLongMap<StoredNode>,
    initialPersistentMarkerCount: Int,
  ) {
    private val builder: PersistentLongMapBuilder<StoredNode> = states.builder()
    private var persistentMarkerCount: Int = initialPersistentMarkerCount

    /**
     * Smallest old end offset of a marker whose policy changes its offsets or invalidates it.
     * [Int.MAX_VALUE] means that no such marker was found.
     */
    var firstChangedEnd: Int = Int.MAX_VALUE
      private set

    /** Updates [firstChangedEnd] from a policy result in absolute document coordinates. */
    fun recordTransform(entry: MarkerEntry, result: MarkerTransformResult) {
      if (!result.isValid || result.startOffset != entry.nodeStart || result.endOffset != entry.nodeEnd) {
        firstChangedEnd = minOf(firstChangedEnd, entry.nodeEnd)
      }
    }

    fun state(markerId: Long): StoredNode? = builder.getUnchecked(markerId)

    fun valid(markerId: Long): ValidNode = builder.getUnchecked(markerId) as? ValidNode
                                           ?: throw IllegalStateException("Marker $markerId is not a valid tree node: ${
                                             builder.getUnchecked(markerId)
                                           }")

    fun insert(rootId: Long, entry: MarkerEntry): Long {
      val markerId = entry.handle.order
      require(entry.nodeStart >= 0) { "startOffset must be non-negative but got: ${entry.nodeStart}" }
      require(entry.nodeEnd >= entry.nodeStart) {
        "endOffset must not precede startOffset, but got: (${entry.nodeStart}, ${entry.nodeEnd})"
      }
      val existingState = state(markerId)
      require(existingState == null || existingState === AbsentNode) { "Marker $markerId already exists: $existingState" }
      assert(existingState !== AbsentNode) { "Marker ID $markerId must not be reused" }
      putValid(
        markerId,
        ValidNode(
          entry,
          parentId = NULL_NODE,
          leftId = NULL_NODE,
          rightId = NULL_NODE,
          height = 1,
          maximumEndOffset = entry.nodeEnd,
          lazyOffsetDelta = 0,
          subtreeFlavorFlags = entry.flavorFlags,
          subtreeAggregate = entry.measure,
        )
      )
      addPolicy(entry.policy)
      val newRoot = insertAvl(this, rootId, markerId)
      setParent(newRoot, NULL_NODE)
      return newRoot
    }

    fun ancestorDelta(state: ValidNode): Int {
      var parentId = state.parentId
      var delta = 0
      while (parentId != NULL_NODE) {
        val parent = valid(parentId)
        delta += parent.lazyOffsetDelta
        parentId = parent.parentId
      }
      return delta
    }

    fun putValid(markerId: Long, node: ValidNode) {
      builder.put(markerId, node)
    }

    fun putInvalid(entry: MarkerEntry, reason: String) {
      persistentMarkerCount = decrementPersistentMarkerCount(persistentMarkerCount, entry.policy)
      builder.put(
        entry.handle.order,
        InvalidNode(reason, TextRangeScalarUtil.toScalarRange(entry.nodeStart, entry.nodeEnd))
      )
    }

    fun putAbsent(markerId: Long) {
      builder.put(markerId, AbsentNode)
    }

    fun remove(markerId: Long) {
      builder.remove(markerId)
    }

    fun addPolicy(policy: MarkerPolicy) {
      persistentMarkerCount = incrementPersistentMarkerCount(persistentMarkerCount, policy)
    }

    fun removePolicy(policy: MarkerPolicy) {
      persistentMarkerCount = decrementPersistentMarkerCount(persistentMarkerCount, policy)
    }

    fun setParent(markerId: Long, parentId: Long) {
      if (markerId == NULL_NODE) return
      val node = valid(markerId)
      if (node.parentId != parentId) putValid(markerId, node.copy(parentId = parentId))
    }

    fun build(): PersistentLongMap<StoredNode> = builder.build()

    fun persistentMarkerCount(): Int = persistentMarkerCount
  }

  companion object {
    private const val ALL_FLAVOR_FLAGS: Int = 0xFF
    private const val UNCACHED_OFFSETS: Long = -1L
    private val CACHED_OFFSETS_UPDATER: AtomicReferenceFieldUpdater<PMarkerRootImpl, ConcurrentLongLongMap> =
      AtomicReferenceFieldUpdater.newUpdater(
        PMarkerRootImpl::class.java, ConcurrentLongLongMap::class.java, "cachedOffsets")
    private val ENTRY_COMPARATOR: Comparator<MarkerEntry> =
      Comparator { first, second -> PositionKey(first).compareTo(PositionKey(second)) }
    private const val NULL_NODE: Long = 0

    private object EMPTY : PMarkerRootImpl(
      NULL_NODE,
      PersistentLongMap.empty(),
      0,
    ) {
      override fun toString(): String = "EMPTY"
    }

    fun empty(): PMarkerRootImpl = EMPTY

    /** Registers an inserted range before roots share the cache. Rejects conflicting ranges for the same handle. */
    private fun cacheInsertedOffsets(cache: ConcurrentLongLongMap, entry: MarkerEntry) {
      val offsets = TextRangeScalarUtil.toScalarRange(entry.nodeStart, entry.nodeEnd)
      val cached = cache.putIfAbsent(entry.handle.order, offsets)
      assert(cached == UNCACHED_OFFSETS || cached == offsets) { "The marker handle must not be reused with different offsets" }
    }

    private fun validatePatch(patch: DocumentPatch, beforeText: DocumentText, afterText: DocumentText) {
      val startOffset = patch.startOffset()
      val endOffset = patch.endOffset()
      val newLength = patch.newFragment().length
      require(startOffset >= 0) { "startOffset must be non-negative but got: $startOffset" }
      require(endOffset >= startOffset) { "endOffset must not precede startOffset, but got: ($startOffset, $endOffset)" }
      require(endOffset <= beforeText.length()) { "DocumentTextPatch range exceeds source text length: $endOffset > ${beforeText.length()}" }
      require(afterText.length().toLong() == beforeText.length().toLong() - (endOffset - startOffset) + newLength) {
        "DocumentTextPatch is inconsistent with the target text length"
      }
      require(startOffset <= Int.MAX_VALUE - newLength) { "DocumentTextPatch new range overflows Int: startOffset=$startOffset, newLength=$newLength" }
      require(patch.moveOffset() >= 0) { "DocumentTextPatch moveOffset must be non-negative but got: $patch" }
      require(patch.moveOffset() <= Int.MAX_VALUE - newLength) { "DocumentTextPatch move range overflows Int: $patch" }
    }

    private fun incrementPersistentMarkerCount(
      count: Int,
      policy: MarkerPolicy,
    ): Int {
      return if (policy.isPersistent) count + 1 else count
    }

    private fun decrementPersistentMarkerCount(
      count: Int,
      policy: MarkerPolicy,
    ): Int {
      return if (policy.isPersistent) count - 1 else count
    }

    private fun key(markerId: Long, node: ValidNode): PositionKey = PositionKey(node.entry.nodeStart, markerId)

    private fun height(editor: MapBatchEditor, markerId: Long): Int = if (markerId != NULL_NODE) editor.valid(markerId).height else 0

    private fun containsAllFlavorFlags(flavorFlags: Byte, requiredFlavorFlags: Int): Boolean =
      (flavorFlags.toInt() and requiredFlavorFlags) == requiredFlavorFlags

    private fun subtreeFlavorFlags(editor: MapBatchEditor, markerId: Long): Int =
      if (markerId == NULL_NODE) 0 else editor.valid(markerId).subtreeFlavorFlags.toInt()

    private fun subtreeFlavorFlags(editor: MapBatchEditor, entry: MarkerEntry, leftId: Long, rightId: Long): Byte =
      (entry.flavorFlags.toInt() or subtreeFlavorFlags(editor, leftId) or subtreeFlavorFlags(editor, rightId)).toByte()

    private fun subtreeAggregate(editor: MapBatchEditor, markerId: Long): Int =
      if (markerId == NULL_NODE) 0 else editor.valid(markerId).subtreeAggregate

    private fun subtreeAggregate(editor: MapBatchEditor, entry: MarkerEntry, leftId: Long, rightId: Long): Int =
      entry.measure + subtreeAggregate(editor, leftId) + subtreeAggregate(editor, rightId)

    private fun balanceFactor(editor: MapBatchEditor, node: ValidNode): Int {
      return height(editor, node.leftId) - height(editor, node.rightId)
    }

    private fun shift(editor: MapBatchEditor, nodeId: Long, delta: Int) {
      if (nodeId == NULL_NODE || delta == 0) return
      val node = editor.valid(nodeId)
      editor.putValid(
        nodeId,
        node.copy(
          entry = node.entry.copy(
            nodeStart = node.entry.nodeStart + delta,
            nodeEnd = node.entry.nodeEnd + delta,
          ),
          maximumEndOffset = node.maximumEndOffset + delta,
          lazyOffsetDelta = node.lazyOffsetDelta + delta
        )
      )
    }

    private fun push(editor: MapBatchEditor, nodeId: Long): ValidNode {
      val node = editor.valid(nodeId)
      val delta = node.lazyOffsetDelta
      if (delta == 0) return node

      shift(editor, node.leftId, delta)
      shift(editor, node.rightId, delta)
      val updated = node.copy(lazyOffsetDelta = 0)
      editor.putValid(nodeId, updated)
      return updated
    }

    private fun rewrite(
      editor: MapBatchEditor,
      markerId: Long,
      node: ValidNode,
      parentId: Long,
      leftId: Long,
      rightId: Long,
      entry: MarkerEntry = node.entry,
    ) {
      check(node.lazyOffsetDelta == 0) { "Node $markerId must be pushed before it is rewritten" }
      val updated = node.copy(
        entry = entry,
        parentId = parentId,
        leftId = leftId,
        rightId = rightId,
        height = maxOf(height(editor, leftId), height(editor, rightId)) + 1,
        maximumEndOffset = maxOf(
          entry.nodeEnd,
          if (leftId != NULL_NODE) editor.valid(leftId).maximumEndOffset else Int.MIN_VALUE,
          if (rightId != NULL_NODE) editor.valid(rightId).maximumEndOffset else Int.MIN_VALUE
        ),
        lazyOffsetDelta = 0,
        subtreeFlavorFlags = subtreeFlavorFlags(editor, entry, leftId, rightId),
        subtreeAggregate = subtreeAggregate(editor, entry, leftId, rightId),
      )
      if (updated != node) editor.putValid(markerId, updated)
      editor.setParent(leftId, markerId)
      editor.setParent(rightId, markerId)
    }

    private fun detachAsLeaf(editor: MapBatchEditor, markerId: Long) {
      val node = push(editor, markerId)
      editor.setParent(node.leftId, NULL_NODE)
      editor.setParent(node.rightId, NULL_NODE)
      rewrite(editor, markerId, node, NULL_NODE, NULL_NODE, NULL_NODE)
    }

    private fun rotateLeft(editor: MapBatchEditor, rootId: Long): Long {
      val root = push(editor, rootId)
      val rightId = checkNotNull(root.rightId)
      val right = push(editor, rightId)
      val parentId = root.parentId
      val middleId = right.leftId

      rewrite(editor, rootId, root, rightId, root.leftId, middleId)
      rewrite(editor, rightId, right, parentId, rootId, right.rightId)
      return rightId
    }

    private fun rotateRight(editor: MapBatchEditor, rootId: Long): Long {
      val root = push(editor, rootId)
      val leftId = checkNotNull(root.leftId)
      val left = push(editor, leftId)
      val parentId = root.parentId
      val middleId = left.rightId

      rewrite(editor, rootId, root, leftId, middleId, root.rightId)
      rewrite(editor, leftId, left, parentId, left.leftId, rootId)
      return leftId
    }

    private fun rebalance(editor: MapBatchEditor, rootId: Long): Long {
      var root = push(editor, rootId)
      val factor = balanceFactor(editor, root)

      if (factor > 1) {
        val leftId = root.leftId
        checkNotNullNode(leftId)
        val left = push(editor, leftId)
        if (balanceFactor(editor, left) < 0) {
          val newLeft = rotateLeft(editor, leftId)
          root = push(editor, rootId)
          rewrite(editor, rootId, root, root.parentId, newLeft, root.rightId)
        }
        return rotateRight(editor, rootId)
      }

      if (factor < -1) {
        val rightId = root.rightId
        checkNotNullNode(rightId)
        val right = push(editor, rightId)
        if (balanceFactor(editor, right) > 0) {
          val newRight = rotateRight(editor, rightId)
          root = push(editor, rootId)
          rewrite(editor, rootId, root, root.parentId, root.leftId, newRight)
        }
        return rotateLeft(editor, rootId)
      }

      return rootId
    }

    private fun insertAvl(editor: MapBatchEditor, rootId: Long, markerId: Long): Long {
      if (rootId == NULL_NODE) return markerId

      val root = push(editor, rootId)
      val inserted = editor.valid(markerId)
      if (key(markerId, inserted) < key(rootId, root)) {
        val newLeft = insertAvl(editor, root.leftId, markerId)
        rewrite(editor, rootId, root, root.parentId, newLeft, root.rightId)
      }
      else {
        val newRight = insertAvl(editor, root.rightId, markerId)
        rewrite(editor, rootId, root, root.parentId, root.leftId, newRight)
      }
      return rebalance(editor, rootId)
    }

    private fun removeByKey(editor: MapBatchEditor, rootId: Long, target: PositionKey): Long {
      if (rootId == NULL_NODE) return NULL_NODE
      val root = push(editor, rootId)
      val comparison = target.compareTo(key(rootId, root))

      if (comparison < 0) {
        val newLeft = removeByKey(editor, root.leftId, target)
        rewrite(editor, rootId, root, root.parentId, newLeft, root.rightId)
        return rebalance(editor, rootId)
      }

      if (comparison > 0) {
        val newRight = removeByKey(editor, root.rightId, target)
        rewrite(editor, rootId, root, root.parentId, root.leftId, newRight)
        return rebalance(editor, rootId)
      }

      val parentId = root.parentId
      if (root.leftId == NULL_NODE) {
        editor.setParent(root.rightId, parentId)
        return root.rightId
      }
      if (root.rightId == NULL_NODE) {
        editor.setParent(root.leftId, parentId)
        return root.leftId
      }

      val extracted = extractMinimum(editor, root.rightId)
      val successor = push(editor, extracted.minimumId)
      rewrite(editor, extracted.minimumId, successor, parentId, root.leftId, extracted.rootId)
      return rebalance(editor, extracted.minimumId)
    }

    private fun extractMinimum(editor: MapBatchEditor, rootId: Long): ExtractMinimumResult {
      var minimumId = rootId
      var minimum = push(editor, minimumId)
      val subtreeParentId = minimum.parentId
      while (minimum.leftId != NULL_NODE) {
        minimumId = minimum.leftId
        minimum = push(editor, minimumId)
      }

      var ancestorId = minimum.parentId
      var remainingRoot = minimum.rightId
      editor.setParent(remainingRoot, minimum.parentId)
      rewrite(editor, minimumId, minimum, NULL_NODE, NULL_NODE, NULL_NODE)

      while (ancestorId != subtreeParentId) {
        val ancestor = editor.valid(ancestorId)
        val parentId = ancestor.parentId
        rewrite(editor, ancestorId, ancestor, parentId, remainingRoot, ancestor.rightId)
        remainingRoot = rebalance(editor, ancestorId)
        ancestorId = parentId
      }
      return ExtractMinimumResult(remainingRoot, minimumId)
    }

    private fun checkNotNullNode(id: Long) {
      check(id != NULL_NODE)
    }

    private fun joinPrepared(editor: MapBatchEditor, leftId: Long, pivotId: Long, rightId: Long): Long {
      val leftHeight = height(editor, leftId)
      val rightHeight = height(editor, rightId)

      if (leftHeight <= rightHeight + 1 && rightHeight <= leftHeight + 1) {
        val pivot = push(editor, pivotId)
        rewrite(editor, pivotId, pivot, NULL_NODE, leftId, rightId)
        return pivotId
      }

      val leftIsTaller = leftHeight > rightHeight
      val shorterHeight = if (leftIsTaller) rightHeight else leftHeight
      var subtreeId = if (leftIsTaller) leftId else rightId
      val subtreeParentId = editor.valid(subtreeId).parentId
      var ancestorId = subtreeParentId
      while (height(editor, subtreeId) > shorterHeight + 1) {
        val root = push(editor, subtreeId)
        ancestorId = subtreeId
        subtreeId = if (leftIsTaller) root.rightId else root.leftId
      }

      val pivot = push(editor, pivotId)
      rewrite(editor, pivotId, pivot, NULL_NODE,
              if (leftIsTaller) subtreeId else leftId,
              if (leftIsTaller) rightId else subtreeId)
      var result = pivotId
      while (ancestorId != subtreeParentId) {
        val ancestor = editor.valid(ancestorId)
        val parentId = ancestor.parentId
        rewrite(editor, ancestorId, ancestor, parentId,
                if (leftIsTaller) ancestor.leftId else result,
                if (leftIsTaller) result else ancestor.rightId)
        result = rebalance(editor, ancestorId)
        ancestorId = parentId
      }
      editor.setParent(result, NULL_NODE)
      return result
    }

    private fun joinDisjoint(editor: MapBatchEditor, leftId: Long, rightId: Long): Long {
      if (leftId == NULL_NODE) {
        editor.setParent(rightId, NULL_NODE)
        return rightId
      }
      if (rightId == NULL_NODE) {
        editor.setParent(leftId, NULL_NODE)
        return leftId
      }

      editor.setParent(leftId, NULL_NODE)
      editor.setParent(rightId, NULL_NODE)
      val extracted = extractMinimum(editor, rightId)
      val result = joinPrepared(editor, leftId, extracted.minimumId, extracted.rootId)
      editor.setParent(result, NULL_NODE)
      return result
    }

    private fun splitByStart(
      editor: MapBatchEditor,
      rootId: Long,
      boundaryOffset: Int,
      equalGoesLeft: Boolean,
    ): Pair<Long, Long> {
      if (rootId == NULL_NODE) return NULL_NODE to NULL_NODE

      val root = push(editor, rootId)
      val leftId = root.leftId
      val rightId = root.rightId
      val goesLeft = root.entry.nodeStart < boundaryOffset || equalGoesLeft && root.entry.nodeStart == boundaryOffset
      detachAsLeaf(editor, rootId)

      return if (goesLeft) {
        val [middle, greater] = splitByStart(editor, rightId, boundaryOffset, equalGoesLeft)
        val lessOrEqual = joinPrepared(editor, leftId, rootId, middle)
        editor.setParent(lessOrEqual, NULL_NODE)
        editor.setParent(greater, NULL_NODE)
        lessOrEqual to greater
      }
      else {
        val [less, middle] = splitByStart(editor, leftId, boundaryOffset, equalGoesLeft)
        val greaterOrEqual = joinPrepared(editor, middle, rootId, rightId)
        editor.setParent(less, NULL_NODE)
        editor.setParent(greaterOrEqual, NULL_NODE)
        less to greaterOrEqual
      }
    }

    private fun updateMarkersStartingBeforeEdit(
      editor: MapBatchEditor,
      rootId: Long,
      patch: DocumentPatch,
      beforeText: DocumentText,
      afterText: DocumentText,
      invalidatedMarkerConsumer: Consumer<in SnapshotMarkerReference<*>>,
      affectedMarkerConsumer: Consumer<in SnapshotMarkerReference<*>>,
    ): Long {
      if (rootId == NULL_NODE) return NULL_NODE
      val initial = editor.valid(rootId)
      if (initial.maximumEndOffset < patch.startOffset()) return rootId

      val root = push(editor, rootId)
      val leftId = root.leftId
      val rightId = root.rightId
      val entry = root.entry
      detachAsLeaf(editor, rootId)

      val newLeft = updateMarkersStartingBeforeEdit(
        editor, leftId, patch, beforeText, afterText, invalidatedMarkerConsumer, affectedMarkerConsumer
      )
      val newRight = updateMarkersStartingBeforeEdit(
        editor, rightId, patch, beforeText, afterText, invalidatedMarkerConsumer, affectedMarkerConsumer
      )

      val update = transform(
        editor, entry, patch, beforeText, afterText, invalidatedMarkerConsumer, affectedMarkerConsumer
      )
      return if (update.isValid) {
        check(update.startOffset == entry.nodeStart) {
          "An edit changed the start of a marker that starts before the edit"
        }
        val leaf = editor.valid(rootId)
        rewrite(editor, rootId, leaf, NULL_NODE, NULL_NODE, NULL_NODE, update.applyTo(entry))
        val result = joinPrepared(editor, newLeft, rootId, newRight)
        editor.setParent(result, NULL_NODE)
        result
      }
      else {
        editor.putInvalid(entry, update.errorReason!!)
        val result = joinDisjoint(editor, newLeft, newRight)
        editor.setParent(result, NULL_NODE)
        result
      }
    }

    private fun collectEntries(
      editor: MapBatchEditor,
      rootId: Long,
      ancestorDelta: Int,
      destination: MutableList<in MarkerEntry>,
    ) {
      if (rootId == NULL_NODE) return
      val node = editor.valid(rootId)
      val childDelta = ancestorDelta + node.lazyOffsetDelta
      collectEntries(editor, node.leftId, childDelta, destination)
      destination.add(node.entry.copy(nodeStart = node.entry.nodeStart + ancestorDelta, nodeEnd = node.entry.nodeEnd + ancestorDelta))
      collectEntries(editor, node.rightId, childDelta, destination)
    }

    private fun retargetContainedMarkers(
      editor: MapBatchEditor,
      rootId: Long,
      moveStart: Int,
      moveEnd: Int,
      offsetDelta: Int,
      afterText: DocumentText,
      invalidatedMarkerConsumer: Consumer<in SnapshotMarkerReference<*>>,
      affectedMarkerConsumer: Consumer<in SnapshotMarkerReference<*>>,
    ): Long {
      val affected = ArrayList<MarkerEntry>()
      collectContainedEntries(editor, rootId, 0, moveStart, moveEnd, affected)

      var result = rootId
      for (entry in affected) {
        result = removeByKey(editor, result, PositionKey(entry))
        val retargeted = entry.copy(
          nodeStart = entry.nodeStart + offsetDelta,
          nodeEnd = entry.nodeEnd + offsetDelta,
        )
        val update = afterRetarget(editor, retargeted, afterText, invalidatedMarkerConsumer, affectedMarkerConsumer)
        if (update.isValid) {
          val updatedEntry = update.applyTo(retargeted)
          editor.putValid(
            entry.handle.order,
            ValidNode(
              entry = updatedEntry,
              parentId = NULL_NODE,
              leftId = NULL_NODE,
              rightId = NULL_NODE,
              height = 1,
              maximumEndOffset = updatedEntry.nodeEnd,
              lazyOffsetDelta = 0,
              subtreeFlavorFlags = updatedEntry.flavorFlags,
              subtreeAggregate = updatedEntry.measure,
            )
          )
          result = insertAvl(editor, result, entry.handle.order)
        }
        else {
          editor.putInvalid(retargeted, update.errorReason!!)
        }
        editor.setParent(result, NULL_NODE)
      }
      return result
    }

    private fun afterRetarget(
      editor: MapBatchEditor,
      entry: MarkerEntry,
      afterText: DocumentText,
      invalidatedMarkerConsumer: Consumer<in SnapshotMarkerReference<*>>,
      affectedMarkerConsumer: Consumer<in SnapshotMarkerReference<*>>,
    ): MarkerTransformResult {
      val result = entry.policy.afterRetarget(entry, afterText)
      processTransformResult(
        editor,
        entry,
        result,
        invalidatedMarkerConsumer,
        affectedMarkerConsumer,
      )
      return result
    }

    private fun processTransformResult(
      editor: MapBatchEditor,
      entry: MarkerEntry,
      result: MarkerTransformResult,
      invalidatedMarkerConsumer: Consumer<in SnapshotMarkerReference<*>>,
      affectedMarkerConsumer: Consumer<in SnapshotMarkerReference<*>>,
    ) {
      editor.recordTransform(entry, result)
      if (result.isValid) {
        affectedMarkerConsumer.accept(entry.handle)
      }
      else {
        invalidatedMarkerConsumer.accept(entry.handle)
      }
    }

    private fun collectContainedEntries(
      editor: MapBatchEditor,
      rootId: Long,
      ancestorDelta: Int,
      startOffset: Int,
      endOffset: Int,
      destination: MutableList<MarkerEntry>,
    ) {
      if (rootId == NULL_NODE) return
      val node = editor.valid(rootId)
      val nodeStart = node.entry.nodeStart + ancestorDelta
      val childDelta = ancestorDelta + node.lazyOffsetDelta

      if (nodeStart >= startOffset) {
        collectContainedEntries(editor, node.leftId, childDelta, startOffset, endOffset, destination)
      }
      val nodeEnd = node.entry.nodeEnd + ancestorDelta
      if (nodeStart >= startOffset && nodeEnd <= endOffset) {
        destination.add(node.entry.copy(nodeStart = nodeStart, nodeEnd = nodeEnd))
      }
      if (nodeStart <= endOffset) {
        collectContainedEntries(editor, node.rightId, childDelta, startOffset, endOffset, destination)
      }
    }

    private fun isSortedByPosition(entries: List<MarkerEntry>): Boolean {
      for (index in 1 until entries.size) {
        if (PositionKey(entries[index - 1]) > PositionKey(entries[index])) return false
      }
      return true
    }

    private fun buildBalanced(editor: MapBatchEditor, sortedEntries: List<MarkerEntry>): Long {
      return buildBalanced(editor, sortedEntries, 0, sortedEntries.size, NULL_NODE)
    }

    private fun buildBalanced(
      editor: MapBatchEditor,
      sortedEntries: List<MarkerEntry>,
      fromIndex: Int,
      toIndex: Int,
      parentId: Long,
    ): Long {
      if (fromIndex >= toIndex) return NULL_NODE
      val middleIndex = (fromIndex + toIndex) ushr 1
      val entry = sortedEntries[middleIndex]
      val leftId = buildBalanced(editor, sortedEntries, fromIndex, middleIndex, entry.handle.order)
      val rightId = buildBalanced(editor, sortedEntries, middleIndex + 1, toIndex, entry.handle.order)
      val height = maxOf(height(editor, leftId), height(editor, rightId)) + 1
      val maximumEndOffset = maxOf(
        entry.nodeEnd,
        if (leftId != NULL_NODE) editor.valid(leftId).maximumEndOffset else Int.MIN_VALUE,
        if (rightId != NULL_NODE) editor.valid(rightId).maximumEndOffset else Int.MIN_VALUE
      )
      editor.putValid(
        entry.handle.order,
        ValidNode(
          entry = entry,
          parentId = parentId,
          leftId = leftId,
          rightId = rightId,
          height = height,
          maximumEndOffset = maximumEndOffset,
          lazyOffsetDelta = 0,
          subtreeFlavorFlags = subtreeFlavorFlags(editor, entry, leftId, rightId),
          subtreeAggregate = subtreeAggregate(editor, entry, leftId, rightId),
        )
      )
      return entry.handle.order
    }

    private fun transform(
      editor: MapBatchEditor,
      entry: MarkerEntry,
      patch: DocumentPatch,
      beforeText: DocumentText,
      afterText: DocumentText,
      invalidatedMarkerConsumer: Consumer<in SnapshotMarkerReference<*>>,
      affectedMarkerConsumer: Consumer<in SnapshotMarkerReference<*>>,
    ): MarkerTransformResult {
      val result = entry.policy.transform(entry, patch, beforeText, afterText)
      processTransformResult(
        editor,
        entry,
        result,
        invalidatedMarkerConsumer,
        affectedMarkerConsumer,
      )
      return result
    }
  }
}
