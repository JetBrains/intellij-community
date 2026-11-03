// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import com.intellij.openapi.editor.ex.DocumentPatch
import com.intellij.openapi.editor.ex.DocumentText
import com.intellij.openapi.editor.impl.marker.PMarkerRoot.MarkerDeltaProcessor
import com.intellij.openapi.editor.impl.marker.PMarkerRoot.MarkerEntry
import com.intellij.openapi.util.TextRangeScalarUtil
import com.intellij.util.ObjectUtilsRt
import com.intellij.util.Processor
import com.intellij.util.containers.ConcurrentLongLongMap
import com.intellij.util.containers.Java11Shim
import org.jetbrains.annotations.TestOnly
import java.util.ArrayDeque
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater
import java.util.function.Consumer

/**
 * Stores one persistent AVL snapshot with direct child links.
 * The tree retains valid nodes. [invalids] retains invalid states and removal tombstones.
 * Handles select states and parents through weak histories for [version].
 * Updates copy changed paths and publish histories after the new root retains the resulting tree.
 *
 * ![Snapshot ownership diagram](pmarker-direct-structure.svg)
 *
 * @property version the revision used for state and parent lookup.
 * @property root the valid subtree, or null when this snapshot has no valid markers.
 * @property invalids the invalid and removed states retained by this snapshot, keyed by handle ID.
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
  @Volatile private var cachedResolutions: ConcurrentLongLongMap? = null,
  private val resolutionUpdate: PMarkerResolutionUpdate? = null,
) : PMarkerRoot {
  override fun advanceResolution(previousIdentity: Any, resolution: Long): Long =
    resolutionUpdate?.advance(previousIdentity, resolution) ?: PMarkerResolution.UNRESOLVED

  override fun isEmpty(): Boolean = root == null

  override fun isValid(handle: SnapshotMarkerReference<*>): Boolean {
    val resolution = cachedResolutions?.get(handle.id) ?: PMarkerResolution.UNRESOLVED
    if (resolution != PMarkerResolution.UNRESOLVED) return true
    return keepingRootAlive {
      val node = state(handle) as? PMarkerNode ?: return@keepingRootAlive false
      containsNode(node)
    }
  }

  /** Checks membership by following versioned parents until this root is reached. */
  private fun containsNode(initial: PMarkerNode): Boolean {
    var node = initial
    while (node !== root) node = node.parents.get(version) ?: return false
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

  private fun resolveState(state: PMarkerNodeState?): Long = when (state) {
    null -> PMarkerResolution.ABSENT
    is PMarkerInvalidNode -> if (state.reason == 0) PMarkerResolution.ABSENT else PMarkerResolution.invalid(state.range)
    is PMarkerNode -> absoluteOffsets(state)
  }

  /** Records a valid resolution after this snapshot checks the marker state and offsets. */
  private fun cacheResolution(id: Long, resolution: Long) {
    var cache = cachedResolutions
    if (cache == null) {
      cache = Java11Shim.createConcurrentLongLongMap(PMarkerResolution.UNRESOLVED)
      if (!CACHED_RESOLUTIONS_UPDATER.compareAndSet(this, null, cache)) {
        cache = cachedResolutions
      }
    }
    cache!!.putIfAbsent(id, resolution)
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
    spec: Byte,
    flavorFlags: Byte,
    measure: Int,
    policy: MarkerPolicy,
  ): PMarkerDirectRoot = keepingRootAlive {
    val entry = MarkerEntry(handle, startOffset, endOffset, spec, flavorFlags, measure, policy)
    val editor = Editor()
    editor.finish(editor.insert(root, handle, entry))
  }

  override fun insertMany(entries: List<MarkerEntry>): PMarkerDirectRoot = keepingRootAlive {
    if (entries.isEmpty()) return@keepingRootAlive this
    val editor = Editor()
    var result = root
    for (entry in entries) result = editor.insert(result, entry.handle, entry)
    editor.finish(result)
  }

  /** Restores supplied offsets after removing their old states. The new snapshot starts with an empty resolution cache. */
  override fun restoreEntries(entries: List<MarkerEntry>): PMarkerDirectRoot {
    if (entries.isEmpty()) return this
    return purgeMany(entries.map { it.handle }).insertMany(entries)
  }

  override fun updateSpec(handle: SnapshotMarkerReference<*>, spec: Byte): PMarkerDirectRoot =
    update(handle, { it.spec == spec }) { it.copy(spec = spec) }

  override fun updateFlavor(handle: SnapshotMarkerReference<*>, flavorFlags: Byte): PMarkerDirectRoot =
    update(handle, { it.flavorFlags == flavorFlags }) { it.copy(flavorFlags = flavorFlags) }

  override fun updateMeasure(handle: SnapshotMarkerReference<*>, measure: Int): PMarkerDirectRoot =
    update(handle, { it.measure == measure }) { it.copy(measure = measure) }

  /**
   * Changes a valid marker's metadata while sharing the resolution cache and its identity.
   * The transform must preserve marker offsets and handle identity.
   */
  private fun update(
    handle: SnapshotMarkerReference<*>,
    unchanged: (MarkerEntry) -> Boolean,
    transform: (MarkerEntry) -> MarkerEntry,
  ): PMarkerDirectRoot = keepingRootAlive {
    val node = state(handle) as? PMarkerNode ?: return@keepingRootAlive this
    if (unchanged(node.entry)) return@keepingRootAlive this
    val offsets = absoluteOffsets(node)
    if (!PMarkerResolution.isValid(offsets)) return@keepingRootAlive this
    val editor = Editor()
    val result = editor.update(root, TextRangeScalarUtil.startOffset(offsets), node.handle, transform)
    editor.finish(result, resolutionCacheIdentity, resolutionUpdate)
  }

  override fun remove(handle: SnapshotMarkerReference<*>): PMarkerDirectRoot = keepingRootAlive {
    val previous = state(handle) ?: return@keepingRootAlive this
    if (previous is PMarkerInvalidNode && previous.reason == 0) return@keepingRootAlive this
    val offsets = resolveState(previous)
    if (PMarkerResolution.isAbsent(offsets)) return@keepingRootAlive this
    val editor = Editor()
    val result = if (previous is PMarkerNode) {
      editor.delete(root, TextRangeScalarUtil.startOffset(offsets), previous.handle)
    }
    else root
    editor.recordInvalid(handle, 0, 0)
    editor.finish(result)
  }

  /** Removes each distinct handle in one version and discards its entry from [invalids]. */
  override fun purgeMany(handles: Collection<SnapshotMarkerReference<*>>): PMarkerDirectRoot = keepingRootAlive {
    if (handles.isEmpty()) return@keepingRootAlive this
    val editor = Editor()
    var result = root
    var changed = false
    val processed = HashSet<SnapshotMarkerReference<*>>()
    for (handle in handles) {
      if (!processed.add(handle)) continue
      val previous = state(handle) ?: continue
      val offsets = resolveState(previous)
      if (previous is PMarkerNode) {
        if (!PMarkerResolution.isValid(offsets)) continue
        result = editor.delete(result, TextRangeScalarUtil.startOffset(offsets), handle)
      }
      editor.pending[handle] = PMarkerNodeHistory.purgedState
      editor.invalidsBuilder.remove(handle.id)
      changed = true
    }
    if (changed) editor.finish(result) else this
  }

  /**
   * Merges valid markers from another root.
   * Existing valid markers and invalid or removed states in this root take precedence.
   */
  override fun mergeValidMarkersFrom(other: PMarkerRoot): PMarkerDirectRoot = keepingRootAlive {
    require(other is PMarkerDirectRoot) { "The roots must use the same implementation" }
    val editor = Editor()
    var result = root
    other.processAllRangeMarkers { entry, delta ->
      val handle = entry.handle
      if (invalids.getUnchecked(handle.id) == null && !isValid(handle)) {
        result = editor.insert(result, handle, entry.shifted(delta))
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
    val start = patch.startOffset()
    val end = patch.endOffset()
    val length = patch.newFragment().length
    require(start >= 0 && end in start..beforeText.length()) { "The patch range is outside the source text" }
    require(afterText.length().toLong() == beforeText.length().toLong() - (end - start) + length) {
      "The patch does not match the target text length"
    }
    require(start <= Int.MAX_VALUE - length && patch.moveOffset() in 0..Int.MAX_VALUE - length) { "The patch range overflows Int" }
    if (root == null || start == end && length == 0) return@keepingRootAlive this
    val editor = Editor()
    val transform = MarkerTransformer { entry ->
      val result = entry.policy.transform(entry, patch, beforeText, afterText)
      editor.transformed(entry, result, invalidatedMarkerConsumer, affectedMarkerConsumer)
      result
    }
    var result: PMarkerNode?
    val fullTraversal = root.persistentCount > 0 && PersistentMarkerPolicy.requiresFullTraversal(patch, beforeText, afterText)
    if (fullTraversal) {
      result = editor.rebuild(root, transform)
    }
    else {
      val [before, fromStart] = editor.split(root, start, false)
      val [middle, after] = editor.split(fromStart, end, true)
      val updatedBefore = editor.updateBefore(before, start, transform)
      val rebuilt = editor.rebuild(middle, transform)
      result = editor.join(editor.join(updatedBefore, rebuilt), editor.shift(after, length - (end - start)))
    }
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
        if (update.isValid) result = editor.insertNode(result, handle, update.applyTo(entry))
      }
    }
    val resolutionUpdate = if (!fullTraversal && patch.moveOffset() == start) {
      PMarkerResolutionUpdate(resolutionCacheIdentity, minOf(start, editor.firstChangedEnd), end, length - (end - start))
    }
    else null
    editor.finish(result, resolutionUpdate = resolutionUpdate)
  }

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
    processor: Processor<in MarkerEntry>,
  ): Boolean {
    require(startOffset in 0..endOffset) { "The query range is invalid" }
    return processOverlaps(root, 0, startOffset, endOffset, tastePreference and 0xFF, processor)
  }

  /**
   * Traverses subtrees that can match the range and flavor flags.
   * Returned entries include all accumulated lazy shifts.
   */
  override fun overlappingIterator(startOffset: Int, endOffset: Int, tastePreference: Int): Iterator<MarkerEntry> {
    require(startOffset in 0..endOffset) { "The query range is invalid" }
    val flags = tastePreference and 0xFF
    return object : Iterator<MarkerEntry> {
      private val stack = ArrayDeque<Frame>()
      private var next: MarkerEntry? = null

      init {
        pushLeft(root, 0)
      }

      override fun hasNext(): Boolean {
        while (next == null && stack.isNotEmpty()) {
          val (node, delta) = stack.removeLast()
          pushLeft(node.right, delta + node.lazyDelta)
          if (node.entry.nodeEnd + delta >= startOffset && matches(node.entry.flavorFlags, flags)) {
            next = node.entry.copy(nodeStart = node.entry.nodeStart + delta, nodeEnd = node.entry.nodeEnd + delta)
          }
        }
        return next != null
      }

      override fun next(): MarkerEntry {
        if (!hasNext()) throw NoSuchElementException()
        val result = next!!
        next = null
        return result
      }

      private fun pushLeft(initial: PMarkerNode?, initialDelta: Int) {
        var node = initial
        var delta = initialDelta
        while (node != null) {
          if (node.maximumEnd + delta < startOffset || !matches(node.flavorFlags, flags)) return
          if (node.entry.nodeStart + delta <= endOffset) stack.addLast(Frame(node, delta))
          delta += node.lazyDelta
          node = node.left
        }
      }
    }
  }

  override fun processAllRangeMarkers(processor: MarkerDeltaProcessor): Boolean = processAll(root, 0, processor)

  /**
   * Reads this version's history for a handle.
   * An invalid or removed state must also appear by identity in [invalids].
   * Callers must check whether a valid node belongs to this root.
   */
  private fun state(handle: SnapshotMarkerReference<*>): PMarkerNodeState? {
    val state = handle.states.get(version)
    return if (state is PMarkerInvalidNode && invalids.getUnchecked(handle.id) !== state) null else state
  }

  /**
   * Resolves absolute offsets by summing lazy shifts along versioned parents.
   * A cached ancestor can end the walk after its history confirms the same node.
   * A missing parent yields [PMarkerResolution.ABSENT]. The caller must keep this root alive during the walk.
   */
  private fun absoluteOffsets(initial: PMarkerNode): Long {
    if (root == null) return PMarkerResolution.ABSENT
    val cache = cachedResolutions
    val cached = cache?.get(initial.handle.id) ?: PMarkerResolution.UNRESOLVED
    if (cached != PMarkerResolution.UNRESOLVED) return cached
    var node = initial
    var delta = 0
    while (node !== root) {
      val parent = node.parents.get(version) ?: return PMarkerResolution.ABSENT
      delta += parent.lazyDelta
      node = parent
      val parentOffsets = cache?.get(node.handle.id) ?: PMarkerResolution.UNRESOLVED
      if (parentOffsets != PMarkerResolution.UNRESOLVED && state(node.handle) === node) {
        delta += TextRangeScalarUtil.startOffset(parentOffsets) - node.entry.nodeStart
        break
      }
    }
    return TextRangeScalarUtil.toScalarRange(initial.entry.nodeStart + delta, initial.entry.nodeEnd + delta)
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

  /** Keeps a traversal node and the total lazy shift from its ancestors. */
  private data class Frame(val node: PMarkerNode, val delta: Int)

  private fun interface MarkerTransformer {
    fun transform(entry: MarkerEntry): MarkerTransformResult
  }

  /**
   * Builds one successor version by copying nodes owned by existing roots.
   * [pending] tracks intermediate states. [finish] publishes invalid and removed states and the final tree links.
   * Each editor is confined to one update.
   */
  private inner class Editor {
    /** Allocates one successor version on first use. */
    val version: PMarkerNodeVersion by lazy(LazyThreadSafetyMode.NONE) {
      PMarkerNodeHistory.nextVersion(this@PMarkerDirectRoot.version)
    }

    /** Tracks the latest state created for each handle, including nodes that later leave the final tree. */
    val pending: MutableMap<SnapshotMarkerReference<*>, PMarkerNodeState> = IdentityHashMap()

    /** Builds the map of invalid and removed states for this version from the existing [invalids]. */
    val invalidsBuilder: PersistentLongMapBuilder<PMarkerInvalidNode> = invalids.builder()

    /** The smallest old end offset changed by a policy, or [Int.MAX_VALUE] when no policy changes a range or validity. */
    var firstChangedEnd: Int = Int.MAX_VALUE
      private set

    /** Creates a node in this editor's version and records its state for later edits. */
    fun make(
      entry: MarkerEntry,
      left: PMarkerNode? = null,
      right: PMarkerNode? = null,
      delta: Int = 0,
    ): PMarkerNode {
      val node = PMarkerNode(version, entry, left, right, delta)
      pending[node.handle] = node
      return node
    }

    /** Copies the subtree root with shifted offsets and defers the same shift to both children. */
    fun shift(node: PMarkerNode?, delta: Int): PMarkerNode? {
      if (node == null || delta == 0) return node
      val entry = node.entry.copy(nodeStart = node.entry.nodeStart + delta, nodeEnd = node.entry.nodeEnd + delta)
      return make(entry, node.left, node.right, node.lazyDelta + delta)
    }

    /** Propagates the pending shift into copies of the children and clears the shift on the replacement node. */
    private fun push(node: PMarkerNode): PMarkerNode {
      if (node.lazyDelta == 0) return node
      return make(node.entry, shift(node.left, node.lazyDelta), shift(node.right, node.lazyDelta))
    }

    /**
     * Checks the handle and range before inserting a new marker.
     * Reads [pending] first to detect repeated handles within one batch.
     */
    fun insert(root: PMarkerNode?, handle: SnapshotMarkerReference<*>, entry: MarkerEntry): PMarkerNode {
      require(entry.nodeStart >= 0 && entry.nodeEnd >= entry.nodeStart) { "The marker range is invalid" }
      var existing = pending[handle] ?: state(handle)
      if (existing is PMarkerNode && !pending.containsKey(handle) && !isValid(handle)) existing = null
      require(existing == null || existing is PMarkerInvalidNode && existing.reason == 0) { "The marker already exists" }
      assert(existing == null) { "The marker handle must not be reused" }
      invalidsBuilder.remove(handle.id)
      return insertNode(root, handle, entry)
    }

    /**
     * Inserts by start offset and handle ID, copying and balancing the search path.
     * The caller must check whether the handle already exists.
     */
    fun insertNode(initial: PMarkerNode?, handle: SnapshotMarkerReference<*>, entry: MarkerEntry): PMarkerNode {
      if (initial == null) {
        invalidsBuilder.remove(handle.id)
        return make(entry)
      }
      val node = push(initial)
      val inserted = if (compare(entry.nodeStart, handle, node.entry.nodeStart, node.handle) < 0) {
        make(node.entry, insertNode(node.left, handle, entry), node.right)
      }
      else {
        make(node.entry, node.left, insertNode(node.right, handle, entry))
      }
      return balance(inserted)
    }

    /** Copies the search path to replace an entry. The transform must preserve the entry's ordering key. */
    fun update(initial: PMarkerNode?, start: Int, handle: SnapshotMarkerReference<*>, transform: (MarkerEntry) -> MarkerEntry): PMarkerNode? {
      if (initial == null) return null
      val node = push(initial)
      val order = compare(start, handle, node.entry.nodeStart, node.handle)
      return when {
        order < 0 -> make(node.entry, update(node.left, start, handle, transform), node.right)
        order > 0 -> make(node.entry, node.left, update(node.right, start, handle, transform))
        else -> make(transform(node.entry), node.left, node.right)
      }
    }

    /** Removes an entry from the valid subtree. The caller records any invalid or removed state separately. */
    fun delete(initial: PMarkerNode?, start: Int, handle: SnapshotMarkerReference<*>): PMarkerNode? {
      if (initial == null) return null
      val node = push(initial)
      val order = compare(start, handle, node.entry.nodeStart, node.handle)
      return when {
        order < 0 -> balance(make(node.entry, delete(node.left, start, handle), node.right))
        order > 0 -> balance(make(node.entry, node.left, delete(node.right, start, handle)))
        else -> {
          pending.remove(node.handle)
          join(node.left, node.right)
        }
      }
    }

    /** Joins ordered subtrees while restoring AVL balance. Every entry in [left] must precede every entry in [right]. */
    fun join(left: PMarkerNode?, right: PMarkerNode?): PMarkerNode? {
      if (left == null) return right
      if (right == null) return left
      val [minimum, remaining] = extractMinimum(right)
      return join(left, minimum.entry, remaining)
    }

    private fun extractMinimum(initial: PMarkerNode): Pair<PMarkerNode, PMarkerNode?> {
      val node = push(initial)
      if (node.left == null) return node to node.right
      val [minimum, remaining] = extractMinimum(node.left)
      return minimum to balance(make(node.entry, remaining, node.right))
    }

    /** Joins ordered subtrees around [entry], which must follow all entries in [left] and precede all entries in [right]. */
    private fun join(left: PMarkerNode?, entry: MarkerEntry, right: PMarkerNode?): PMarkerNode {
      val leftHeight = left?.height ?: 0
      val rightHeight = right?.height ?: 0
      if (leftHeight > rightHeight + 1) {
        val node = push(left!!)
        return balance(make(node.entry, node.left, join(node.right, entry, right)))
      }
      if (rightHeight > leftHeight + 1) {
        val node = push(right!!)
        return balance(make(node.entry, join(left, entry, node.left), node.right))
      }
      return make(entry, left, right)
    }

    /**
     * Splits by marker start offset and preserves AVL balance.
     * [equalGoesLeft] selects the side for entries that start at [boundary].
     */
    fun split(initial: PMarkerNode?, boundary: Int, equalGoesLeft: Boolean): Pair<PMarkerNode?, PMarkerNode?> {
      if (initial == null) return null to null
      val node = push(initial)
      return if (node.entry.nodeStart < boundary || equalGoesLeft && node.entry.nodeStart == boundary) {
        val [middle, after] = split(node.right, boundary, equalGoesLeft)
        join(node.left, node.entry, middle) to after
      }
      else {
        val [before, middle] = split(node.left, boundary, equalGoesLeft)
        before to join(middle, node.entry, node.right)
      }
    }

    private fun balance(node: PMarkerNode): PMarkerNode {
      val factor = (node.left?.height ?: 0) - (node.right?.height ?: 0)
      if (factor > 1) {
        val left = node.left!!
        val prepared = if ((left.left?.height ?: 0) < (left.right?.height ?: 0)) {
          make(node.entry, rotateLeft(left), node.right)
        }
        else node
        return rotateRight(prepared)
      }
      if (factor < -1) {
        val right = node.right!!
        val prepared = if ((right.left?.height ?: 0) > (right.right?.height ?: 0)) {
          make(node.entry, node.left, rotateRight(right))
        }
        else node
        return rotateLeft(prepared)
      }
      return node
    }

    private fun rotateLeft(initial: PMarkerNode): PMarkerNode {
      val node = push(initial)
      val right = push(node.right!!)
      val left = make(node.entry, node.left, right.left)
      return make(right.entry, left, right.right)
    }

    private fun rotateRight(initial: PMarkerNode): PMarkerNode {
      val node = push(initial)
      val left = push(node.left!!)
      val right = make(node.entry, left.right, node.right)
      return make(left.entry, left.left, right)
    }

    /**
     * Transforms the prefix whose markers start before [start], skipping subtrees that end before the edit.
     * A valid result must preserve each marker's start offset.
     */
    fun updateBefore(initial: PMarkerNode?, start: Int, transform: MarkerTransformer): PMarkerNode? {
      if (initial == null || initial.maximumEnd < start) return initial
      val node = push(initial)
      val left = updateBefore(node.left, start, transform)
      val right = updateBefore(node.right, start, transform)
      val result = transform.transform(node.entry)
      if (!result.isValid) return join(left, right)
      check(result.startOffset == node.entry.nodeStart) { "An edit changed a marker start before the edit" }
      return join(left, result.applyTo(node.entry), right)
    }

    /** Transforms a subtree and builds a balanced tree from valid results, sorting them when their order changes. */
    fun rebuild(node: PMarkerNode?, transform: MarkerTransformer): PMarkerNode? {
      val valid = ArrayList<MarkerEntry>()
      processAll(node, 0) { storedEntry, delta ->
        val entry = storedEntry.shifted(delta)
        val result = transform.transform(entry)
        if (result.isValid) valid.add(result.applyTo(entry))
        true
      }
      if ((1 until valid.size).any { compare(valid[it - 1], valid[it]) > 0 }) {
        valid.sortWith { first, second -> compare(first, second) }
      }
      return build(valid, 0, valid.size)
    }

    /** Builds a balanced tree from entries sorted by start offset and handle ID in `[from, to)`. */
    private fun build(entries: List<MarkerEntry>, from: Int, to: Int): PMarkerNode? {
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

    /** Records an invalid state or removal tombstone for this version. A zero [reason] marks removal. */
    fun recordInvalid(handle: SnapshotMarkerReference<*>, range: Long, reason: Int) {
      val state = PMarkerInvalidNode(range, reason)
      pending[handle] = state
      invalidsBuilder.put(handle.id, state)
    }

    /**
     * Creates a root that retains the final tree and invalid and removed states, then publishes their weak histories.
     * Pass the current [cacheIdentity] to share cached resolutions when marker offsets and validity remain unchanged.
     */
    fun finish(
      node: PMarkerNode?,
      cacheIdentity: Any = Any(),
      resolutionUpdate: PMarkerResolutionUpdate? = null,
    ): PMarkerDirectRoot {
      val resolutions = if (cacheIdentity === resolutionCacheIdentity) cachedResolutions else null
      val result = PMarkerDirectRoot(version, node, invalidsBuilder.build(), cacheIdentity, resolutions, resolutionUpdate)
      for ([handle, state] in pending) {
        if (state is PMarkerInvalidNode) handle.states.put(version, state)
      }
      publish(node)
      return result
    }

    /**
     * Publishes states and parent links for nodes created in this version.
     * Recursion stops at shared nodes because their descendants keep the same states and parent links.
     */
    private fun publish(node: PMarkerNode?) {
      if (node == null || node.version !== version) return
      node.handle.states.put(version, node)
      node.left?.parents?.put(version, node)
      node.right?.parents?.put(version, node)
      publish(node.left)
      publish(node.right)
    }
  }

  companion object {
    private val CACHED_RESOLUTIONS_UPDATER: AtomicReferenceFieldUpdater<PMarkerDirectRoot, ConcurrentLongLongMap> =
      AtomicReferenceFieldUpdater.newUpdater(
        PMarkerDirectRoot::class.java, ConcurrentLongLongMap::class.java, "cachedResolutions")

    /** Creates an empty root without inherited marker states. */
    fun empty(): PMarkerDirectRoot {
      return PMarkerDirectRoot(
        PMarkerNodeHistory.initialVersion, null, PersistentLongMap.empty(),
      )
    }

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
      processor: Processor<in MarkerEntry>,
    ): Boolean {
      var current = node
      var currentDelta = delta
      while (true) {
        if (current == null || current.maximumEnd + currentDelta < start || !matches(current.flavorFlags, flags)) return true
        val childDelta = currentDelta + current.lazyDelta
        if (!processOverlaps(current.left, childDelta, start, end, flags, processor)) return false
        (val nodeStart, val nodeEnd, val flavorFlags) = current.entry
        if (nodeStart + currentDelta <= end && nodeEnd + currentDelta >= start && matches(flavorFlags, flags)) {
          if (!processor.process(current.entry)) return false
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
        entries.add(node.entry.copy(nodeStart = nodeStart, nodeEnd = node.entry.nodeEnd + delta))
      }
      if (nodeStart <= end) collectContained(node.right, childDelta, start, end, entries)
    }
  }
}
