// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import com.intellij.openapi.editor.ex.DocumentPatch
import com.intellij.openapi.editor.ex.DocumentText
import com.intellij.openapi.editor.impl.marker.PMarkerRoot.MarkerDeltaProcessor
import com.intellij.openapi.editor.impl.marker.PMarkerRoot.MarkerEntry
import com.intellij.openapi.util.TextRangeScalarUtil
import com.intellij.util.ObjectUtilsRt
import com.intellij.util.containers.ConcurrentLongLongMap
import com.intellij.util.containers.Java11Shim
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap
import org.jetbrains.annotations.TestOnly
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater
import java.util.function.Consumer

/**
 * Stores one persistent AVL snapshot with direct child links.
 * The tree retains valid nodes. [invalids] retains invalid states. Removal records [PMarkerNodeHistory.removedState] in the histories.
 * Handles select positions and invalid states through weak histories for [version].
 * Updates copy changed paths. Position changes publish histories after the new root retains the resulting tree.
 *
 * ![Snapshot ownership diagram](pmarker-direct-structure.svg)
 *
 * @property version the revision used for state and parent lookup.
 * @property root the valid subtree, or null when this snapshot has no valid markers.
 * @property invalids the invalid states retained by this snapshot, keyed by handle ID.
 * @property resolutionCacheIdentity the token shared by roots with unchanged marker offsets and validity.
 * @property cachedResolutions the valid resolutions already checked for this snapshot.
 * Only roots with the same resolution identity share it.
 * @property resolutionUpdate the edit that can advance cached offsets from the previous identity.
 */
class PMarkerDirectRoot private constructor(
  private val version: PMarkerNodeVersion,
  private val root: PMarkerNode?,
  private val invalids: PersistentLongMap<PMarkerInvalidNode>,
  override val resolutionCacheIdentity: Any = Any(),
  @Volatile private var cachedResolutions: ResolutionCache? = null,
  private val resolutionUpdate: PMarkerResolutionUpdate? = null,
) : PMarkerRoot {
  override fun advanceResolution(handle: SnapshotMarkerReference<*>, previousIdentity: Any, resolution: Long): Long =
    resolutionUpdate?.advance(previousIdentity, resolution) ?: PMarkerResolution.UNRESOLVED

  override fun isEmpty(): Boolean = root == null

  /** Whether all valid markers have zero length and measure, and no policy is persistent. */
  internal val canDeferPointInsertions: Boolean
    get() = root?.let { it.pointsOnly && it.persistentCount == 0 } ?: true

  /** Whether any valid marker uses a persistent policy. */
  internal val hasPersistentMarkers: Boolean
    get() = (root?.persistentCount ?: 0) > 0

  /** Returns whether every valid marker ends before [offset]. */
  internal fun endsBefore(offset: Int): Boolean = root == null || root.maximumEnd < offset

  override fun isValid(handle: SnapshotMarkerReference<*>): Boolean {
    val resolution = cachedResolutions?.get(handle.id) ?: PMarkerResolution.UNRESOLVED
    if (resolution != PMarkerResolution.UNRESOLVED) return true
    return keepingRootAlive {
      val node = state(handle) as? PMarkerValidNode ?: return@keepingRootAlive false
      containsNode(node)
    }
  }

  /** Checks membership by following versioned parents until this root is reached. */
  private fun containsNode(initial: PMarkerValidNode): Boolean {
    var node = initial
    while (node !== root?.state) node = node.parents.get(version) ?: return false
    return true
  }

  /** Reuses this snapshot's validated resolutions. A cache miss checks the state and follows versioned parents. */
  override fun resolve(handle: SnapshotMarkerReference<*>): Long {
    val cached = cachedResolutions?.get(handle.id) ?: PMarkerResolution.UNRESOLVED
    if (cached != PMarkerResolution.UNRESOLVED) return cached
    return keepingRootAlive {
      val resolution = resolveState(state(handle))
      if (PMarkerResolution.isValid(resolution)) cacheResolution(handle.id, resolution)
      resolution
    }
  }

  /** Checks [startOffsetHint] against this tree before using the versioned histories to resolve [handle]. */
  internal fun resolveForRemoval(handle: SnapshotMarkerReference<*>, startOffsetHint: Int): Long {
    val cached = cachedResolutions?.get(handle.id) ?: PMarkerResolution.UNRESOLVED
    if (cached != PMarkerResolution.UNRESOLVED) return cached
    var node = root
    var delta = 0
    while (node != null) {
      val order = compare(startOffsetHint, handle, node.entry.nodeStart + delta, node.handle)
      if (order == 0) {
        val resolution = TextRangeScalarUtil.toScalarRange(node.entry.nodeStart + delta, node.entry.nodeEnd + delta)
        cacheResolution(handle.id, resolution)
        return resolution
      }
      delta += node.lazyDelta
      node = if (order < 0) node.left else node.right
    }
    return resolve(handle)
  }

  private fun resolveState(state: PMarkerNodeState?): Long = when (state) {
    null -> PMarkerResolution.ABSENT
    is PMarkerInvalidNode -> if (state.reason == 0) PMarkerResolution.ABSENT else PMarkerResolution.invalid(state.range)
    is PMarkerValidNode -> absoluteOffsets(state)
  }

  /** Records a valid resolution after this snapshot checks the marker state and offsets. */
  private fun cacheResolution(id: Long, resolution: Long) {
    var cache = cachedResolutions
    if (cache == null) {
      cache = ResolutionCache(id, resolution)
      if (CACHED_RESOLUTIONS_UPDATER.compareAndSet(this, null, cache)) return
      cache = cachedResolutions
    }
    cache!!.put(id, resolution)
  }

  private fun cacheResolution(entry: MarkerEntry, ancestorDelta: Int) {
    cacheResolution(entry.handle.id, TextRangeScalarUtil.toScalarRange(entry.nodeStart + ancestorDelta, entry.nodeEnd + ancestorDelta))
  }

  @TestOnly
  override fun invalidationReason(handle: SnapshotMarkerReference<*>): String? = keepingRootAlive {
    val state = state(handle) as? PMarkerInvalidNode ?: return@keepingRootAlive null
    if (state.reason == 0) null else MarkerTransformError.messageForCode(state.reason)
  }

  override fun insert(
    handle: SnapshotMarkerReference<*>,
    startOffset: Int,
    endOffset: Int,
    specMask: Byte,
    flavorFlags: Byte,
    measure: Int,
    policy: MarkerPolicy,
  ): PMarkerDirectRoot = keepingRootAlive {
    val entry = MarkerEntry(handle, startOffset, endOffset, specMask, flavorFlags, measure, policy)
    val editor = Editor()
    editor.finish(editor.insert(root, entry), resolutionUpdate = insertionResolutionUpdate())
  }

  override fun insertMany(entries: List<MarkerEntry>): PMarkerDirectRoot = keepingRootAlive {
    if (entries.isEmpty()) return@keepingRootAlive this
    val editor = Editor()
    var result = root
    val inserted = HashSet<SnapshotMarkerReference<*>>()
    for (entry in entries) {
      require(inserted.add(entry.handle)) { "The marker already exists" }
      result = editor.insert(result, entry)
    }
    editor.finish(result, resolutionUpdate = insertionResolutionUpdate())
  }

  /** Builds a tree from sorted entries whose ranges and handles have already been checked. */
  internal fun buildFromSortedEntries(entries: List<MarkerEntry>): PMarkerDirectRoot = keepingRootAlive {
    check(isEmpty())
    if (entries.isEmpty()) return@keepingRootAlive this
    val editor = Editor()
    val result = editor.finish(editor.build(entries, 0, entries.size))
    val resolutions = Long2LongOpenHashMap(entries.size)
    resolutions.defaultReturnValue(PMarkerResolution.UNRESOLVED)
    for (entry in entries) {
      resolutions.put(entry.handle.id, TextRangeScalarUtil.toScalarRange(entry.nodeStart, entry.nodeEnd))
    }
    result.cachedResolutions = ResolutionCache(-1, PMarkerResolution.UNRESOLVED, resolutions)
    result
  }

  internal fun checkNewHandle(handle: SnapshotMarkerReference<*>) {
    val existing = state(handle)
    require(existing == null || existing is PMarkerValidNode && !isValid(handle)) { "The marker already exists" }
    assert(!isRemoved(handle)) { "The marker handle must not be reused" }
  }

  private fun insertionResolutionUpdate(): PMarkerResolutionUpdate =
    PMarkerResolutionUpdate(resolutionCacheIdentity, Int.MAX_VALUE, Int.MAX_VALUE, 0, resolutionUpdate)

  /** Restores supplied offsets after removing their old states. The new snapshot starts with an empty resolution cache. */
  override fun restoreEntries(entries: List<MarkerEntry>): PMarkerDirectRoot {
    if (entries.isEmpty()) return this
    return purgeMany(entries.map { it.handle }).insertMany(entries)
  }

  /** Replaces valid point markers. Large batches rebuild the ordered tree in one version. */
  internal fun restorePointEntries(entries: List<MarkerEntry>): PMarkerDirectRoot = keepingRootAlive {
    if (entries.isEmpty()) return@keepingRootAlive this
    require(entries.all { it.nodeStart >= 0 && it.nodeStart == it.nodeEnd && it.measure == 0 }) { "The marker must be a point" }
    val height = root?.height ?: 0
    if (entries.size < 16 || entries.size.toLong() * height < (1L shl height)) {
      val editor = Editor()
      var result = root
      var changeStart = Int.MAX_VALUE
      var changeEnd = 0
      val handles = if (entries.size > 1) HashSet<SnapshotMarkerReference<*>>() else null
      for (entry in entries) {
        require(handles?.add(entry.handle) != false) { "The marker already exists" }
        val previous = resolve(entry.handle)
        require(PMarkerResolution.isValid(previous)) { "The relocated marker must be valid" }
        val start = PMarkerResolution.startOffset(previous)
        changeStart = minOf(changeStart, start, entry.nodeStart)
        changeEnd = maxOf(changeEnd, PMarkerResolution.endOffset(previous), entry.nodeEnd)
        result = editor.delete(result, start, entry.handle)
        result = editor.insertNode(result, entry)
      }
      val restored = editor.finish(
        result, PMarkerResolutionUpdate(resolutionCacheIdentity, changeStart, changeEnd, 0, resolutionUpdate),
      )
      for (entry in entries) restored.cacheResolution(entry, 0)
      return@keepingRootAlive restored
    }
    val replacements = PersistentLongMap.empty<MarkerEntry>().builder()
    for (entry in entries) {
      require(replacements.getUnchecked(entry.handle.id) == null) { "The marker already exists" }
      replacements.put(entry.handle.id, entry)
    }
    val combined = ArrayList<MarkerEntry>(entries.size)
    var found = 0
    processAll(root, 0) { entry, delta ->
      val replacement = replacements.getUnchecked(entry.handle.id)
      if (replacement != null) found++
      combined.add(replacement ?: entry.shifted(delta))
      true
    }
    require(found == entries.size) { "The relocated marker must be valid" }
    rebuildEntries(combined)
  }

  /**
   * Applies insertions whose affected policies were checked by [PMarkerInsertionRoot].
   * Preserves marker handles and metadata. Small batches update paths, and larger batches rebuild the tree.
   */
  internal fun applyInsertions(insertions: PMarkerRelocationRoot.Insertions): PMarkerDirectRoot {
    val height = root?.height ?: 0
    if (insertions.size < 16 || insertions.size.toLong() * height < (1L shl height)) {
      var result = this
      insertions.forEach { offset, length -> result = result.applyBufferedInsertion(offset, length) }
      return result
    }
    val entries = ArrayList<MarkerEntry>()
    processAll(root, 0) { entry, delta ->
      val start = entry.nodeStart + delta
      val end = entry.nodeEnd + delta
      entries.add(entry.copy(nodeStart = start + insertions.deltaAt(start), nodeEnd = end + insertions.deltaAt(end)))
      true
    }
    return rebuildEntries(entries)
  }

  /** Applies an insertion whose policies and strict interior ranges were checked by [PMarkerInsertionRoot]. */
  private fun applyBufferedInsertion(offset: Int, length: Int): PMarkerDirectRoot = keepingRootAlive {
    val editor = Editor()
    val node = editor.updateOutsideStarts(root, offset, offset, length) { entry ->
      MarkerTransformResult(entry.nodeStart, entry.nodeEnd + if (entry.nodeEnd > offset) length else 0)
    }
    editor.finish(node, PMarkerResolutionUpdate(resolutionCacheIdentity, offset, offset, length, resolutionUpdate))
  }

  /** Rebuilds all valid entries in one version. The entries must preserve the marker handles and metadata. */
  internal fun rebuildEntries(entries: MutableList<MarkerEntry>): PMarkerDirectRoot = keepingRootAlive {
    if ((1 until entries.size).any { compare(entries[it - 1], entries[it]) > 0 }) {
      entries.sortWith { first, second -> compare(first, second) }
    }
    PMarkerNodeHistory.collect(entries.size.coerceAtMost(Int.MAX_VALUE / 2) * 2)
    val editor = Editor()
    val result = editor.finish(editor.build(entries, 0, entries.size))
    for (entry in entries) result.cacheResolution(entry, 0)
    result
  }

  override fun updateSpec(handle: SnapshotMarkerReference<*>, specMask: Byte): PMarkerDirectRoot =
    update(handle, updateAggregates = false) { if (it.specMask == specMask) it else it.copy(specMask = specMask) }

  override fun updateFlavor(handle: SnapshotMarkerReference<*>, flavorFlags: Byte): PMarkerDirectRoot =
    update(handle) { if (it.flavorFlags == flavorFlags) it else it.copy(flavorFlags = flavorFlags) }

  override fun updateMeasure(handle: SnapshotMarkerReference<*>, measure: Int): PMarkerDirectRoot =
    update(handle) { if (it.measure == measure) it else it.copy(measure = measure) }

  /**
   * Changes a valid marker's metadata while sharing the resolution cache and its identity.
   * The transform must preserve marker offsets and handle identity. It returns the same entry when the metadata stays unchanged.
   */
  private fun update(
    handle: SnapshotMarkerReference<*>,
    updateAggregates: Boolean = true,
    transform: (MarkerEntry) -> MarkerEntry,
  ): PMarkerDirectRoot = keepingRootAlive {
    val offsets = resolve(handle)
    if (!PMarkerResolution.isValid(offsets)) return@keepingRootAlive this
    val tree = updateMetadata(
      checkNotNull(root), TextRangeScalarUtil.startOffset(offsets), handle, 0, updateAggregates, transform,
    )
    if (tree === root) this else PMarkerDirectRoot(version, tree, invalids, resolutionCacheIdentity, cachedResolutions, resolutionUpdate)
  }

  /** Copies the metadata path without changing positions or propagating lazy shifts. */
  private fun updateMetadata(
    node: PMarkerNode,
    start: Int,
    handle: SnapshotMarkerReference<*>,
    delta: Int,
    updateAggregates: Boolean,
    transform: (MarkerEntry) -> MarkerEntry,
  ): PMarkerNode {
    val order = compare(start, handle, node.entry.nodeStart + delta, node.handle)
    return when {
      order < 0 -> {
        val left = updateMetadata(checkNotNull(node.left), start, handle, delta + node.lazyDelta, updateAggregates, transform)
        if (left === node.left) node else node.withMetadata(left = left, updateAggregates = updateAggregates)
      }
      order > 0 -> {
        val right = updateMetadata(checkNotNull(node.right), start, handle, delta + node.lazyDelta, updateAggregates, transform)
        if (right === node.right) node else node.withMetadata(right = right, updateAggregates = updateAggregates)
      }
      else -> {
        val entry = transform(node.entry)
        if (entry === node.entry) node else node.withMetadata(entry = entry, updateAggregates = updateAggregates)
      }
    }
  }

  /** Records the removal in the new version so a later merge skips this handle. */
  override fun remove(handle: SnapshotMarkerReference<*>): PMarkerDirectRoot = keepingRootAlive {
    val previous = state(handle) ?: return@keepingRootAlive this
    val offsets = resolveState(previous)
    if (PMarkerResolution.isAbsent(offsets)) return@keepingRootAlive this
    val editor = Editor()
    val result = if (previous is PMarkerValidNode) {
      editor.delete(root, TextRangeScalarUtil.startOffset(offsets), previous.handle)
    }
    else root
    editor.recordRemoval(handle)
    val resolutionUpdate = if (previous is PMarkerValidNode) {
      PMarkerResolutionUpdate(
        resolutionCacheIdentity, TextRangeScalarUtil.startOffset(offsets), TextRangeScalarUtil.endOffset(offsets), 0,
        this@PMarkerDirectRoot.resolutionUpdate,
      )
    }
    else null
    editor.finish(result, resolutionUpdate = resolutionUpdate)
  }

  /** Purges each distinct handle in one version. A purge also replaces a removal and discards the entry from [invalids]. */
  override fun purgeMany(handles: Collection<SnapshotMarkerReference<*>>): PMarkerDirectRoot = keepingRootAlive {
    if (handles.isEmpty()) return@keepingRootAlive this
    val editor = Editor()
    var result = root
    var changed = false
    val processed = HashSet<SnapshotMarkerReference<*>>()
    for (handle in handles) {
      if (!processed.add(handle)) continue
      val previous = state(handle)
      if (previous == null) {
        if (!isRemoved(handle)) continue
      }
      else if (previous is PMarkerValidNode) {
        val offsets = resolveState(previous)
        if (!PMarkerResolution.isValid(offsets)) continue
        result = editor.delete(result, TextRangeScalarUtil.startOffset(offsets), handle)
      }
      editor.recordPurge(handle)
      changed = true
    }
    if (changed) editor.finish(result) else this
  }

  /**
   * Merges valid markers from another root.
   * Existing valid markers, invalid states and removals in this root take precedence.
   */
  override fun mergeValidMarkersFrom(other: PMarkerRoot): PMarkerDirectRoot = keepingRootAlive {
    if (other is PMarkerDirectRoot && (other.root == null || root === other.root)) return@keepingRootAlive this
    val editor = Editor()
    var result = root
    other.processAllRangeMarkers { entry, delta ->
      val handle = entry.handle
      if (invalids.getUnchecked(handle.id) == null && !isRemoved(handle) && !isValid(handle)) {
        result = editor.insert(result, entry.shifted(delta))
      }
      true
    }
    if (result === root) this else editor.finish(result)
  }

  /**
   * Transforms markers around the edit and shifts later subtrees lazily.
   * Persistent marker policies can require a full rebuild.
   * A move also retargets markers fully contained in the moved range.
   */
  override fun applyPatch(
    patch: DocumentPatch,
    beforeText: DocumentText,
    afterText: DocumentText,
    invalidatedMarkerConsumer: Consumer<in SnapshotMarkerReference<*>>,
    affectedMarkerConsumer: Consumer<in SnapshotMarkerReference<*>>,
  ): PMarkerDirectRoot = keepingRootAlive {
    validateMarkerPatch(patch, beforeText, afterText)
    val start = patch.startOffset()
    val end = patch.endOffset()
    val length = patch.newFragment().length
    require(start <= Int.MAX_VALUE - length && patch.moveOffset() in 0..Int.MAX_VALUE - length) { "The patch range overflows Int" }
    if (root == null || start == end && length == 0) return@keepingRootAlive this
    val fullTraversal = root.persistentCount > 0 && PersistentMarkerPolicy.requiresFullTraversal(patch, beforeText, afterText)
    if (!fullTraversal && patch.moveOffset() == start) {
      if (root.maximumEnd < start) return@keepingRootAlive this
      if (end < root.minimumStart || end == root.minimumStart && root.canShiftAtStart && patch.originEndOffset() <= end &&
          affectedMarkerConsumer === PMarkerRoot.EMPTY_HANDLE_CONSUMER) {
        val delta = length - (end - start)
        if (delta == 0) return@keepingRootAlive this
        val editor = Editor()
        return@keepingRootAlive editor.finish(
          editor.shift(root, delta), resolutionUpdate = PMarkerResolutionUpdate(resolutionCacheIdentity, start, end, delta, resolutionUpdate),
        )
      }
    }
    val editor = Editor()
    val transform = MarkerTransformer { entry ->
      val result = entry.policy.transform(entry, patch, beforeText, afterText)
      editor.transformed(entry, result, invalidatedMarkerConsumer, affectedMarkerConsumer)
      result
    }
    var result: PMarkerNode?
    if (fullTraversal) {
      result = editor.rebuild(root, transform)
    }
    else if (patch.moveOffset() == start && !hasStartInRange(start, end)) {
      result = editor.updateOutsideStarts(root, start, end, length - (end - start), transform)
    }
    else {
      val [before, fromStart] = editor.split(root, start, false)
      val [middle, after] = editor.split(fromStart, end, true)
      val updatedBefore = editor.updateBefore(before, start, transform)
      val rebuilt = editor.rebuild(middle, transform, minStart = start, maxStart = start + length)
      result = editor.join(editor.join(updatedBefore, rebuilt), editor.shift(after, length - (end - start)))
    }
    result = editor.insertRelocated(result)
    if (start == end && patch.moveOffset() != start) {
      val moveStart = patch.moveOffset()
      val moved = ArrayList<MarkerEntry>()
      collectContained(result, 0, moveStart, moveStart + length, moved)
      for (movedEntry in moved) {
        val handle = movedEntry.handle
        result = editor.delete(result, movedEntry.nodeStart, handle)
        val entry = movedEntry.copy(nodeStart = movedEntry.nodeStart + start - moveStart, nodeEnd = movedEntry.nodeEnd + start - moveStart)
        val update = entry.policy.afterRetarget(entry, afterText)
        editor.transformed(entry, update, invalidatedMarkerConsumer, affectedMarkerConsumer)
        if (update.isValid) result = editor.insertNode(result, update.applyTo(entry))
      }
    }
    val resolutionUpdate = if (!fullTraversal && patch.moveOffset() == start) {
      PMarkerResolutionUpdate(
        resolutionCacheIdentity, minOf(start, editor.firstChangedEnd), end, length - (end - start), this@PMarkerDirectRoot.resolutionUpdate,
      )
    }
    else null
    editor.finish(result, resolutionUpdate = resolutionUpdate)
  }

  private fun hasStartInRange(start: Int, end: Int): Boolean {
    var node = root
    var delta = 0
    while (node != null) {
      val nodeStart = node.entry.nodeStart + delta
      delta += node.lazyDelta
      node = when {
        nodeStart < start -> node.right
        nodeStart > end -> node.left
        else -> return true
      }
    }
    return false
  }

  /** Sums the measures of markers that start at or before [offset]. Uses subtree totals as it follows the search path. */
  override fun getPrefixAggregate(offset: Int): Int {
    var node = root
    var delta = 0
    var result = 0
    while (node != null) {
      val childDelta = delta + node.lazyDelta
      if (node.entry.nodeStart + delta <= offset) {
        result += (node.left?.aggregate ?: 0) + node.entry.measure
        node = node.right
      }
      else node = node.left
      delta = childDelta
    }
    return result
  }

  override fun processRangeMarkersOverlappingWith(
    startOffset: Int,
    endOffset: Int,
    tastePreference: Int,
    processor: MarkerDeltaProcessor,
  ): Boolean {
    require(startOffset in 0..endOffset) { "The query range is invalid" }
    return processOverlaps(root, 0, startOffset, endOffset, tastePreference and 0xFF) { entry, delta ->
      cacheResolution(entry, delta)
      processor.process(entry, delta)
    }
  }

  override fun overlappingIterator(startOffset: Int, endOffset: Int, tastePreference: Int): Iterator<MarkerEntry> {
    if (root == null) {
      require(startOffset in 0..endOffset) { "The query range is invalid" }
      return emptyList<MarkerEntry>().iterator()
    }
    return OverlappingIterator(this, startOffset, endOffset, tastePreference)
  }

  private class OverlappingIterator(
    root: PMarkerDirectRoot,
    startOffset: Int,
    endOffset: Int,
    tastePreference: Int,
  ) : OverlappingEntryTraversal(root, startOffset, endOffset, tastePreference), Iterator<MarkerEntry> {
    override fun hasNext(): Boolean = hasNextEntry()

    override fun next(): MarkerEntry = nextEntry() ?: throw NoSuchElementException()
  }

  /** Traverses a direct root in place and delegates to the root's iterator for other root types. */
  abstract class OverlappingEntryTraversal protected constructor(
    root: PMarkerRoot,
    private val startOffset: Int,
    private val endOffset: Int,
    tastePreference: Int,
  ) {
    private val directRoot: PMarkerDirectRoot? = root as? PMarkerDirectRoot
    private val entries: Iterator<MarkerEntry>? =
      if (directRoot == null) root.overlappingIterator(startOffset, endOffset, tastePreference) else null
    private val flags: Int = tastePreference and 0xFF

    /** Pending nodes. [shifts] stores the ancestor shift at each index. */
    private val nodes: Array<PMarkerNode?>
    private val shifts: IntArray
    private var stackSize: Int = 0
    private var nextEntry: MarkerEntry? = null
    private var nextShift: Int = 0

    init {
      if (directRoot != null) require(startOffset in 0..endOffset) { "The query range is invalid" }
      val tree = directRoot?.root
      nodes = if (tree == null) EMPTY_NODES else arrayOfNulls(tree.height)
      shifts = if (tree == null) EMPTY_SHIFTS else IntArray(tree.height)
      pushLeft(tree, 0)
    }

    /** Tests for a matching entry without consuming it. */
    protected fun hasNextEntry(): Boolean {
      if (entries != null) return entries.hasNext()
      while (nextEntry == null && stackSize > 0) {
        val top = --stackSize
        val node = nodes[top]!!
        val delta = shifts[top]
        nodes[top] = null
        pushLeft(node.right, delta + node.lazyDelta)
        if (node.entry.nodeEnd + delta >= startOffset && matches(node.entry.flavorFlags, flags)) {
          nextEntry = node.entry
          nextShift = delta
        }
      }
      return nextEntry != null
    }

    /** Consumes the next entry with its document offsets, or returns null at the end. */
    protected fun nextEntry(): MarkerEntry? {
      if (entries != null) return if (entries.hasNext()) entries.next() else null
      if (!hasNextEntry()) return null
      val entry = nextEntry!!
      nextEntry = null
      checkNotNull(directRoot).cacheResolution(entry, nextShift)
      return entry.shifted(nextShift)
    }

    /** Queues overlap candidates along the left path. Prunes subtrees by end offset and flavor flags. */
    private fun pushLeft(initial: PMarkerNode?, initialDelta: Int) {
      var node = initial
      var delta = initialDelta
      while (node != null) {
        if (node.maximumEnd + delta < startOffset || !matches(node.subtreeFlavorFlags, flags)) return
        if (node.entry.nodeStart + delta <= endOffset) {
          nodes[stackSize] = node
          shifts[stackSize] = delta
          stackSize++
        }
        delta += node.lazyDelta
        node = node.left
      }
    }

    private companion object {
      val EMPTY_NODES: Array<PMarkerNode?> = emptyArray()
      val EMPTY_SHIFTS: IntArray = IntArray(0)
    }
  }

  override fun processAllRangeMarkers(processor: MarkerDeltaProcessor): Boolean {
    if (cachedResolutions?.allMarkersCached == true) return processAll(root, 0, processor)
    val completed = processAll(root, 0) { entry, delta ->
      cacheResolution(entry, delta)
      processor.process(entry, delta)
    }
    if (completed) cachedResolutions?.allMarkersCached = true
    return completed
  }

  /**
   * Reads this version's history for a handle.
   * An invalid state must also appear by identity in [invalids], so a removal reads as null.
   * Tree entries supply the current metadata. Callers must check whether a valid position belongs to this root.
   */
  private fun state(handle: SnapshotMarkerReference<*>): PMarkerNodeState? {
    val state = handle.states.get(version)
    return if (state is PMarkerInvalidNode && invalids.getUnchecked(handle.id) !== state) null else state
  }

  /** Whether [remove] removed [handle] in this version or an earlier one, and no later record replaces the removal. */
  private fun isRemoved(handle: SnapshotMarkerReference<*>): Boolean = handle.states.get(version) === PMarkerNodeHistory.removedState

  /**
   * Resolves absolute offsets by summing lazy shifts along versioned parents.
   * A cached ancestor can end the walk after its history confirms the same node.
   * A missing parent yields [PMarkerResolution.ABSENT]. The caller must keep this root alive during the walk.
   */
  private fun absoluteOffsets(initial: PMarkerValidNode): Long {
    if (root == null) return PMarkerResolution.ABSENT
    val cache = cachedResolutions
    val cached = cache?.get(initial.handle.id) ?: PMarkerResolution.UNRESOLVED
    if (cached != PMarkerResolution.UNRESOLVED) return cached
    var node = initial
    var delta = 0
    while (node !== root.state) {
      val parent = node.parents.get(version) ?: return PMarkerResolution.ABSENT
      delta += parent.lazyDelta
      node = parent
      val parentOffsets = cache?.get(node.handle.id) ?: PMarkerResolution.UNRESOLVED
      if (parentOffsets != PMarkerResolution.UNRESOLVED && state(node.handle) === node) {
        delta += TextRangeScalarUtil.startOffset(parentOffsets) - node.nodeStart
        break
      }
    }
    return TextRangeScalarUtil.toScalarRange(initial.nodeStart + delta, initial.nodeEnd + delta)
  }

  /**
   * Keeps this root strongly reachable until [action] completes.
   * Weak histories require the root to retain their values throughout lookups.
   */
  private inline fun <T> keepingRootAlive(action: () -> T): T {
    try {
      return action()
    }
    finally {
      ObjectUtilsRt.reachabilityFence(this)
    }
  }

  /** Stores the first resolution directly and allocates a map for other marker IDs. Metadata changes share this cache. */
  private class ResolutionCache(
    private val firstId: Long,
    private val firstResolution: Long,
    private val completeResolutions: Long2LongOpenHashMap? = null,
  ) {
    /**
     * A full traversal sets this flag after it caches every valid marker's absolute offsets and completes normally.
     * The volatile write publishes those offsets, so later traversals and lookups can skip cache writes.
     * Metadata changes preserve this flag. Changes to marker offsets or membership use a new cache.
     */
    @Volatile
    var allMarkersCached: Boolean = completeResolutions != null

    @Volatile
    private var additionalResolutions: ConcurrentLongLongMap? = null

    operator fun get(id: Long): Long =
      if (id == firstId) firstResolution else completeResolutions?.get(id) ?: additionalResolutions?.get(id) ?: PMarkerResolution.UNRESOLVED

    /** Allocates a concurrent cache when a marker needs an entry beyond the first cached resolution. */
    fun put(id: Long, resolution: Long) {
      if (id == firstId || allMarkersCached) return
      var cache = additionalResolutions
      if (cache == null) {
        cache = Java11Shim.createConcurrentLongLongMap(PMarkerResolution.UNRESOLVED)
        if (!ADDITIONAL_RESOLUTIONS_UPDATER.compareAndSet(this, null, cache)) {
          cache = additionalResolutions
        }
      }
      cache!!.putIfAbsent(id, resolution)
    }

    companion object {
      private val ADDITIONAL_RESOLUTIONS_UPDATER: AtomicReferenceFieldUpdater<ResolutionCache, ConcurrentLongLongMap> =
        AtomicReferenceFieldUpdater.newUpdater(ResolutionCache::class.java, ConcurrentLongLongMap::class.java, "additionalResolutions")
    }
  }

  private fun interface MarkerTransformer {
    fun transform(entry: MarkerEntry): MarkerTransformResult
  }

  /**
   * Builds one successor version by copying nodes owned by existing roots.
   * [recordPendingState] tracks invalid states and purge records. [finish] publishes their histories and the final tree links.
   * Each editor is confined to one update.
   */
  private inner class Editor {
    /** The successor version, or null before the first use of [version]. */
    private var allocatedVersion: PMarkerNodeVersion? = null

    /** Allocates one successor version on first use. */
    val version: PMarkerNodeVersion
      get() = allocatedVersion ?: PMarkerNodeHistory.nextVersion(this@PMarkerDirectRoot.version).also { allocatedVersion = it }

    private var firstPendingHandle: SnapshotMarkerReference<*>? = null
    private var firstPendingState: PMarkerInvalidNode? = null
    private var additionalPendingStates: MutableMap<SnapshotMarkerReference<*>, PMarkerInvalidNode>? = null

    /** Builds the map of invalid states for this version from the existing [invalids]. */
    private var invalidsBuilder: PersistentLongMapBuilder<PMarkerInvalidNode>? = null

    private fun editInvalids(): PersistentLongMapBuilder<PMarkerInvalidNode> =
      invalidsBuilder ?: invalids.builder().also { invalidsBuilder = it }

    private fun removeInvalid(id: Long) {
      val builder = invalidsBuilder
      if (builder != null) builder.remove(id)
      else if (invalids.getUnchecked(id) != null) editInvalids().remove(id)
    }

    /** Retains the latest pending state for each handle until [finish] publishes the histories. */
    private fun recordPendingState(handle: SnapshotMarkerReference<*>, state: PMarkerInvalidNode) {
      if (firstPendingHandle == null) {
        firstPendingHandle = handle
        firstPendingState = state
      }
      else if (firstPendingHandle === handle) {
        firstPendingState = state
      }
      else {
        val pending = additionalPendingStates ?: IdentityHashMap<SnapshotMarkerReference<*>, PMarkerInvalidNode>().also {
          additionalPendingStates = it
        }
        pending[handle] = state
      }
    }

    fun recordPurge(handle: SnapshotMarkerReference<*>) {
      recordPendingState(handle, PMarkerNodeHistory.purgedState)
      removeInvalid(handle.id)
    }

    /** Records a removal for this version. Unlike [recordInvalid], it leaves no entry in [invalids]. */
    fun recordRemoval(handle: SnapshotMarkerReference<*>) {
      recordPendingState(handle, PMarkerNodeHistory.removedState)
      removeInvalid(handle.id)
    }

    /** The smallest old end offset changed by a policy, or [Int.MAX_VALUE] when no policy changes a range or validity. */
    var firstChangedEnd: Int = Int.MAX_VALUE
      private set

    /**
     * Holds entries that need new positions in the tree. Their offsets refer to the edited document.
     * The list is null until [deferRelocation] adds the first entry.
     */
    private var relocatedEntries: ArrayList<MarkerEntry>? = null

    /** Reuses an unchanged node or creates a node in this editor's version. */
    fun make(
      entry: MarkerEntry,
      left: PMarkerNode? = null,
      right: PMarkerNode? = null,
      delta: Int = 0,
      source: PMarkerNode? = null,
    ): PMarkerNode {
      if (source != null && entry === source.entry && left === source.left && right === source.right && delta == source.lazyDelta) {
        return source
      }
      return PMarkerNode(version, entry, left, right, delta, source)
    }

    /** Copies the subtree root with shifted offsets and defers the same shift to both children. */
    fun shift(node: PMarkerNode?, delta: Int): PMarkerNode? {
      if (node == null || delta == 0) return node
      val entry = node.entry.copy(nodeStart = node.entry.nodeStart + delta, nodeEnd = node.entry.nodeEnd + delta)
      return make(entry, node.left, node.right, node.lazyDelta + delta, source = node)
    }

    /** Propagates the pending shift into copies of the children and clears the shift on the replacement node. */
    private fun push(node: PMarkerNode): PMarkerNode {
      if (node.lazyDelta == 0) return node
      return make(node.entry, shift(node.left, node.lazyDelta), shift(node.right, node.lazyDelta), source = node)
    }

    /**
     * Checks the handle and range before inserting a new marker.
     * The caller rejects duplicate handles within a batch.
     */
    fun insert(root: PMarkerNode?, entry: MarkerEntry): PMarkerNode {
      require(entry.nodeStart >= 0 && entry.nodeEnd >= entry.nodeStart) { "The marker range is invalid" }
      checkNewHandle(entry.handle)
      return insertNode(root, entry)
    }

    /**
     * Inserts by start offset and handle ID, copying and balancing the search path.
     * The caller must check whether the handle already exists.
     */
    fun insertNode(initial: PMarkerNode?, entry: MarkerEntry): PMarkerNode {
      if (initial == null) {
        removeInvalid(entry.handle.id)
        return make(entry)
      }
      val node = push(initial)
      val inserted = if (compare(entry, node.entry) < 0) {
        make(node.entry, insertNode(node.left, entry), node.right, source = node)
      }
      else {
        make(node.entry, node.left, insertNode(node.right, entry), source = node)
      }
      return balance(inserted)
    }

    /** Removes an entry from the valid subtree. The caller records any invalid or removed state separately. */
    fun delete(initial: PMarkerNode?, start: Int, handle: SnapshotMarkerReference<*>): PMarkerNode? {
      if (initial == null) return null
      val node = push(initial)
      val order = compare(start, handle, node.entry.nodeStart, node.handle)
      return when {
        order < 0 -> balance(make(node.entry, delete(node.left, start, handle), node.right, source = node))
        order > 0 -> balance(make(node.entry, node.left, delete(node.right, start, handle), source = node))
        else -> join(node.left, node.right)
      }
    }

    /** Joins ordered subtrees while restoring AVL balance. Every entry in [left] must precede every entry in [right]. */
    fun join(left: PMarkerNode?, right: PMarkerNode?): PMarkerNode? {
      if (left == null) return right
      if (right == null) return left
      val [minimum, remaining] = extractMinimum(right)
      return join(left, minimum.entry, remaining, minimum)
    }

    private fun extractMinimum(initial: PMarkerNode): Pair<PMarkerNode, PMarkerNode?> {
      val node = push(initial)
      if (node.left == null) return node to node.right
      val [minimum, remaining] = extractMinimum(node.left)
      return minimum to balance(make(node.entry, remaining, node.right, source = node))
    }

    /** Joins ordered subtrees around [entry], which must follow all entries in [left] and precede all entries in [right]. */
    private fun join(left: PMarkerNode?, entry: MarkerEntry, right: PMarkerNode?, source: PMarkerNode? = null): PMarkerNode {
      val leftHeight = left?.height ?: 0
      val rightHeight = right?.height ?: 0
      if (leftHeight > rightHeight + 1) {
        val node = push(left!!)
        return balance(make(node.entry, node.left, join(node.right, entry, right, source), source = node))
      }
      if (rightHeight > leftHeight + 1) {
        val node = push(right!!)
        return balance(make(node.entry, join(left, entry, node.left, source), node.right, source = node))
      }
      return make(entry, left, right, source = source)
    }

    /**
     * Splits by marker start offset and preserves AVL balance.
     * [equalGoesLeft] selects the side for entries that start at [boundary].
     */
    fun split(initial: PMarkerNode?, boundary: Int, equalGoesLeft: Boolean): Pair<PMarkerNode?, PMarkerNode?> {
      if (initial == null) return null to null
      if (initial.maximumEnd < boundary || equalGoesLeft && initial.maximumEnd == boundary) return initial to null
      if (initial.minimumStart > boundary || !equalGoesLeft && initial.minimumStart == boundary) return null to initial
      val node = push(initial)
      return if (node.entry.nodeStart < boundary || equalGoesLeft && node.entry.nodeStart == boundary) {
        val [middle, after] = split(node.right, boundary, equalGoesLeft)
        join(node.left, node.entry, middle, node) to after
      }
      else {
        val [before, middle] = split(node.left, boundary, equalGoesLeft)
        before to join(middle, node.entry, node.right, node)
      }
    }

    /** Restores the AVL height balance with one or two rotations. */
    private fun balance(node: PMarkerNode): PMarkerNode {
      val factor = (node.left?.height ?: 0) - (node.right?.height ?: 0)
      if (factor > 1) {
        val left = node.left!!
        val prepared = if ((left.left?.height ?: 0) < (left.right?.height ?: 0)) {
          make(node.entry, rotateLeft(left), node.right, source = node)
        }
        else node
        return rotateRight(prepared)
      }
      if (factor < -1) {
        val right = node.right!!
        val prepared = if ((right.left?.height ?: 0) > (right.right?.height ?: 0)) {
          make(node.entry, node.left, rotateRight(right), source = node)
        }
        else node
        return rotateLeft(prepared)
      }
      return node
    }

    private fun rotateLeft(initial: PMarkerNode): PMarkerNode {
      val node = push(initial)
      val right = push(node.right!!)
      val left = make(node.entry, node.left, right.left, source = node)
      return make(right.entry, left, right.right, source = right)
    }

    private fun rotateRight(initial: PMarkerNode): PMarkerNode {
      val node = push(initial)
      val left = push(node.left!!)
      val right = make(node.entry, left.right, node.right, source = node)
      return make(left.entry, left.left, right, source = left)
    }

    /** Transforms markers around the edit and defers entries whose starts change. */
    fun updateOutsideStarts(
      initial: PMarkerNode?,
      start: Int,
      end: Int,
      delta: Int,
      transform: MarkerTransformer,
    ): PMarkerNode? {
      if (initial == null || initial.maximumEnd < start) return initial
      if (initial.minimumStart > end) return shift(initial, delta)
      val node = push(initial)
      val left = updateOutsideStarts(node.left, start, end, delta, transform)
      val right = updateOutsideStarts(node.right, start, end, delta, transform)
      val entry = node.entry
      if (entry.nodeStart > end) return join(left, entry.shifted(delta), right, node)
      val result = transform.transform(entry)
      if (!result.isValid) return join(left, right)
      val updated = result.applyTo(entry)
      if (updated.nodeStart != entry.nodeStart) {
        deferRelocation(updated)
        return join(left, right)
      }
      return join(left, updated, right, node)
    }

    /** Transforms the prefix whose markers start before [start], skipping subtrees that end before the edit. */
    fun updateBefore(initial: PMarkerNode?, start: Int, transform: MarkerTransformer): PMarkerNode? =
      updateOutsideStarts(initial, start, Int.MAX_VALUE, 0, transform)

    /**
     * Builds a balanced tree from valid transformed entries with starts in the inclusive range from [minStart] to [maxStart].
     * Defers valid entries with starts outside that range.
     */
    fun rebuild(node: PMarkerNode?, transform: MarkerTransformer, minStart: Int = 0, maxStart: Int = Int.MAX_VALUE): PMarkerNode? {
      if (node == null) return null
      val valid = ArrayList<MarkerEntry>()
      processAll(node, 0) { storedEntry, delta ->
        val entry = storedEntry.shifted(delta)
        val result = transform.transform(entry)
        if (result.isValid) {
          val updated = result.applyTo(entry)
          if (updated.nodeStart in minStart..maxStart) valid.add(updated)
          else deferRelocation(updated)
        }
        true
      }
      if ((1 until valid.size).any { compare(valid[it - 1], valid[it]) > 0 }) {
        valid.sortWith { first, second -> compare(first, second) }
      }
      return build(valid, 0, valid.size)
    }

    /** Queues a valid transformed [entry] for [insertRelocated]. */
    private fun deferRelocation(entry: MarkerEntry) {
      val entries = relocatedEntries ?: ArrayList<MarkerEntry>().also { relocatedEntries = it }
      entries.add(entry)
    }

    /**
     * Inserts each deferred entry into [initial] by its new start offset and handle ID.
     * Call once after the edit updates and joins the remaining subtrees.
     */
    fun insertRelocated(initial: PMarkerNode?): PMarkerNode? {
      val entries = relocatedEntries ?: return initial
      var node = initial
      for (entry in entries) node = insertNode(node, entry)
      return node
    }

    /** Builds a balanced tree from entries sorted by start offset and handle ID in `[from, to)`. */
    fun build(entries: List<MarkerEntry>, from: Int, to: Int): PMarkerNode? {
      if (from == to) return null
      val middle = (from + to) ushr 1
      val entry = entries[middle]
      return make(entry, build(entries, from, middle), build(entries, middle + 1, to))
    }

    /** Reports the policy result. Invalid results also create an invalid state with the input offsets. */
    fun transformed(
      entry: MarkerEntry,
      result: MarkerTransformResult,
      invalidated: Consumer<in SnapshotMarkerReference<*>>,
      affected: Consumer<in SnapshotMarkerReference<*>>,
    ) {
      val handle = entry.handle
      if (!result.isValid || result.startOffset != entry.nodeStart || result.endOffset != entry.nodeEnd) {
        firstChangedEnd = minOf(firstChangedEnd, entry.nodeEnd)
      }
      if (result.isValid) affected.accept(handle)
      else {
        recordInvalid(handle, TextRangeScalarUtil.toScalarRange(entry.nodeStart, entry.nodeEnd), result.range.toInt())
        invalidated.accept(handle)
      }
    }

    /** Records an invalid state for this version. [reason] is the negative code from [MarkerTransformResult.range]. */
    fun recordInvalid(handle: SnapshotMarkerReference<*>, range: Long, reason: Int) {
      val state = PMarkerInvalidNode(range, reason)
      recordPendingState(handle, state)
      editInvalids().put(handle.id, state)
    }

    /** Creates a root that retains the final tree and invalid states, then publishes the weak histories. */
    fun finish(
      node: PMarkerNode?,
      resolutionUpdate: PMarkerResolutionUpdate? = null,
    ): PMarkerDirectRoot {
      if (node === root && invalidsBuilder == null && firstPendingHandle == null) return this@PMarkerDirectRoot
      val result = PMarkerDirectRoot(version, node, invalidsBuilder?.build() ?: invalids, resolutionUpdate = resolutionUpdate)
      firstPendingHandle?.states?.put(version, checkNotNull(firstPendingState))
      additionalPendingStates?.forEach { [handle, state] -> handle.states.put(version, state) }
      publish(node)
      return result
    }

    /**
     * Publishes states and parent links for nodes created in this version.
     * Recursion stops at shared nodes because their descendants keep the same states and parent links.
     */
    private fun publish(node: PMarkerNode?) {
      if (node == null || node.version !== version) return
      node.publish()
      publish(node.left)
      publish(node.right)
    }
  }

  companion object {
    private val CACHED_RESOLUTIONS_UPDATER: AtomicReferenceFieldUpdater<PMarkerDirectRoot, ResolutionCache> =
      AtomicReferenceFieldUpdater.newUpdater(
        PMarkerDirectRoot::class.java, ResolutionCache::class.java, "cachedResolutions")

    private val EMPTY: PMarkerDirectRoot =
      PMarkerDirectRoot(PMarkerNodeHistory.initialVersion, null, PersistentLongMap.empty())

    /** Returns an empty root without inherited marker states. */
    fun empty(): PMarkerDirectRoot = EMPTY

    private fun compare(first: MarkerEntry, second: MarkerEntry): Int =
      compare(first.nodeStart, first.handle, second.nodeStart, second.handle)

    private fun compare(start: Int, handle: SnapshotMarkerReference<*>, otherStart: Int, otherHandle: SnapshotMarkerReference<*>): Int {
      val order = start.compareTo(otherStart)
      return if (order != 0) order else handle.id.compareTo(otherHandle.id)
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun matches(flags: Byte, required: Int): Boolean = flags.toInt() and required == required

    /**
     * Visits matching entries in tree order, pruning subtrees by maximum end offset and flavor flags.
     * [delta] is the total lazy shift from ancestors. The processor receives stored entries.
     */
    private fun processOverlaps(
      node: PMarkerNode?,
      delta: Int,
      start: Int,
      end: Int,
      flags: Int,
      processor: MarkerDeltaProcessor,
    ): Boolean {
      var current = node
      var currentDelta = delta
      while (true) {
        if (current == null || current.maximumEnd + currentDelta < start || !matches(current.subtreeFlavorFlags, flags)) return true
        val childDelta = currentDelta + current.lazyDelta
        if (!processOverlaps(current.left, childDelta, start, end, flags, processor)) return false
        (val nodeStart, val nodeEnd, val flavorFlags) = current.entry
        if (nodeStart + currentDelta <= end && nodeEnd + currentDelta >= start && matches(flavorFlags, flags)) {
          if (!processor.process(current.entry, currentDelta)) return false
        }
        if (nodeStart + currentDelta > end) return true
        current = current.right
        currentDelta = childDelta
      }
    }

    /** Visits every valid entry in tree order and supplies accumulated ancestor shifts separately. */
    private fun processAll(node: PMarkerNode?, delta: Int, processor: MarkerDeltaProcessor): Boolean {
      if (node == null) return true
      val childDelta = delta + node.lazyDelta
      if (!processAll(node.left, childDelta, processor)) return false
      if (!processor.process(node.entry, delta)) return false
      return processAll(node.right, childDelta, processor)
    }

    private fun MarkerEntry.shifted(delta: Int): MarkerEntry =
      if (delta == 0) this else copy(nodeStart = nodeStart + delta, nodeEnd = nodeEnd + delta)

    /** Collects entries fully inside the requested range, with accumulated lazy shifts applied. */
    private fun collectContained(node: PMarkerNode?, delta: Int, start: Int, end: Int, entries: MutableList<MarkerEntry>) {
      if (node == null) return
      val childDelta = delta + node.lazyDelta
      val nodeStart = node.entry.nodeStart + delta
      if (nodeStart >= start) collectContained(node.left, childDelta, start, end, entries)
      if (nodeStart >= start && node.entry.nodeEnd + delta <= end) {
        entries.add(node.entry.shifted(delta))
      }
      if (nodeStart <= end) collectContained(node.right, childDelta, start, end, entries)
    }
  }
}
