// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import com.intellij.openapi.application.impl.BulkArrayQueue
import com.intellij.openapi.editor.impl.marker.PMarkerRoot.MarkerEntry
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.experimental.or

/**
 * Stores a persistent AVL subtree with strong child references.
 * Metadata updates preserve the positions and parent histories of all entries.
 *
 * @property version the revision that creates this subtree.
 * @property state the position before shifts from ancestors.
 * @property entry the current marker metadata and stored offsets.
 * @property height the height used for AVL balancing.
 * @property minimumStart the smallest start offset, including lazy shifts within this subtree.
 * @property canShiftAtStart whether all markers at [minimumStart] use default nonempty ranges and have no left greediness.
 * @property maximumEnd the largest end offset, including lazy shifts within this subtree.
 * @property persistentCount the number of entries whose policies can require a full traversal.
 * @property subtreeFlavorFlags the combined flavor flags used for query pruning.
 * @property aggregate the sum of marker measures within this subtree.
 * @property changedParentMask the mask of parent links that this node must publish for its children.
 * Bit 0 selects [left], and bit 1 selects [right].
 * A clear bit means that the child inherits its parent link from an earlier version.
 */
internal class PMarkerNode private constructor(
  val version: PMarkerNodeVersion,
  val state: PMarkerValidNode,
  val entry: MarkerEntry,
  val left: PMarkerNode?,
  val right: PMarkerNode?,
  val height: Int,
  val minimumStart: Int,
  val canShiftAtStart: Boolean,
  val maximumEnd: Int,
  val persistentCount: Int,
  val subtreeFlavorFlags: Byte,
  val aggregate: Int,
  private val changedParentMask: Byte,
) {
  constructor(
    version: PMarkerNodeVersion,
    entry: MarkerEntry,
    left: PMarkerNode?,
    right: PMarkerNode?,
    lazyDelta: Int = 0,
    source: PMarkerNode? = null,
  ) : this(
    version,
    reusedState(source, entry, lazyDelta) ?: PMarkerValidNode(version, entry.handle, entry.nodeStart, entry.nodeEnd, lazyDelta),
    entry, left, right,
    maxOf(left?.height ?: 0, right?.height ?: 0) + 1,
    minimumStart(entry, left, lazyDelta),
    canShiftAtStart(entry, left, right, lazyDelta),
    maxOf(entry.nodeEnd, shiftedEnd(left, lazyDelta), shiftedEnd(right, lazyDelta)),
    (if (entry.policy.isPersistent) 1 else 0) + (left?.persistentCount ?: 0) + (right?.persistentCount ?: 0),
    entry.flavorFlags or (left?.subtreeFlavorFlags ?: 0) or (right?.subtreeFlavorFlags ?: 0),
    entry.measure + (left?.aggregate ?: 0) + (right?.aggregate ?: 0),
    changedParentMask(version, entry, left, right, lazyDelta, source),
  )

  val handle: SnapshotMarkerReference<*>
    get() = entry.handle
  val lazyDelta: Int
    get() = state.lazyDelta

  /** Publishes this node's state and the changed parent links for its children. */
  fun publish() {
    if (state.version === version) handle.states.put(version, state)
    if (changedParentMask.toInt() and 1 != 0) left?.state?.parents?.put(version, state)
    if (changedParentMask.toInt() and 2 != 0) right?.state?.parents?.put(version, state)
  }

  /** Copies metadata and preserves the shape, offsets, and policies of this subtree. */
  fun withMetadata(
    entry: MarkerEntry = this.entry,
    left: PMarkerNode? = this.left,
    right: PMarkerNode? = this.right,
    updateAggregates: Boolean = true,
  ): PMarkerNode = PMarkerNode(
    version, state, entry, left, right, height, minimumStart, canShiftAtStart(entry, left, right, lazyDelta), maximumEnd, persistentCount,
    if (updateAggregates) entry.flavorFlags or (left?.subtreeFlavorFlags ?: 0) or (right?.subtreeFlavorFlags ?: 0) else subtreeFlavorFlags,
    if (updateAggregates) entry.measure + (left?.aggregate ?: 0) + (right?.aggregate ?: 0) else aggregate,
    changedParentMask,
  )

  companion object {
    private fun minimumStart(entry: MarkerEntry, left: PMarkerNode?, delta: Int): Int =
      if (left == null) entry.nodeStart else left.minimumStart + delta

    private fun canShiftAtStart(entry: MarkerEntry, left: PMarkerNode?, right: PMarkerNode?, delta: Int): Boolean {
      val minimum = minimumStart(entry, left, delta)
      return left?.canShiftAtStart != false &&
             (entry.nodeStart != minimum || entry.policy === DefaultMarkerPolicy &&
              entry.nodeStart < entry.nodeEnd && !MarkerSpec.isGreedyToLeft(entry.spec)) &&
             (right == null || right.minimumStart + delta != minimum || right.canShiftAtStart)
    }

    private fun reusedState(source: PMarkerNode?, entry: MarkerEntry, delta: Int): PMarkerValidNode? = source?.state?.takeIf {
      it.handle === entry.handle && it.nodeStart == entry.nodeStart && it.nodeEnd == entry.nodeEnd && it.lazyDelta == delta
    }

    /**
     * Computes the mask of parent links that this copy must publish.
     * A child inherits its link only when this copy reuses the source state and the child reuses the source child's state.
     * The source must come from an earlier version because copies from this version can have unpublished links.
     */
    private fun changedParentMask(
      version: PMarkerNodeVersion,
      entry: MarkerEntry,
      left: PMarkerNode?,
      right: PMarkerNode?,
      delta: Int,
      source: PMarkerNode?,
    ): Byte {
      if (source == null || source.version === version || reusedState(source, entry, delta) == null) return 3
      return ((if (left?.state === source.left?.state) 0 else 1) or
              (if (right?.state === source.right?.state) 0 else 2)).toByte()
    }

    private fun shiftedEnd(child: PMarkerNode?, delta: Int): Int = if (child == null) Int.MIN_VALUE else child.maximumEnd + delta
  }
}

/** Represents a valid, invalid, or removed marker state in a handle's history. */
internal sealed interface PMarkerNodeState

/**
 * Stores a valid position without retaining child nodes or marker metadata.
 * Copies with unchanged offsets and lazy shifts share this position and its parent history.
 *
 * @property version the revision that publishes this position.
 * @property lazyDelta the shift deferred to children.
 */
internal class PMarkerValidNode(
  val version: PMarkerNodeVersion,
  val handle: SnapshotMarkerReference<*>,
  val nodeStart: Int,
  val nodeEnd: Int,
  val lazyDelta: Int,
) : PMarkerNodeState {
  val parents: PMarkerNodeHistory<PMarkerValidNode> = PMarkerNodeHistory()
}

/**
 * Stores an invalid or removed marker state outside the valid subtree.
 *
 * @property range the offsets packed by [com.intellij.openapi.util.TextRangeScalarUtil]. A removed marker stores zero.
 * @property reason the negative reason code from [MarkerTransformResult.range], or zero for a removed marker.
 */
internal class PMarkerInvalidNode(
  val range: Long,
  val reason: Int,
) : PMarkerNodeState

/**
 * Identifies a revision within one linear branch.
 *
 * @property branch the branch whose history this version reads.
 * @property number the revision number. Each successor adds one. Different branches can use the same number.
 */
internal class PMarkerNodeVersion(val branch: PMarkerNodeBranch, val number: Long)

/**
 * Keeps the version from which a branch starts without retaining preceding roots.
 * History lookup inherits records only through [parent].
 *
 * @property parent the version from which this branch starts, or null for the initial branch.
 */
internal class PMarkerNodeBranch(val parent: PMarkerNodeVersion?, tip: Long) {
  /** Identifies the latest allocated version on this branch. A compare-and-set operation lets one update extend it. */
  val tip: AtomicLong = AtomicLong(tip)
}

/**
 * Selects the last live record on the requested branch, or inherits a record from its branch point.
 * Roots retain the values they need. The history keeps only weak references to those values.
 * Lookup uses binary search on each branch and skips collected records.
 * The latest record on the same branch has a constant-time lookup.
 */
internal class PMarkerNodeHistory<T : Any> {
  /** Caches the last published record for lookups on the same branch. */
  @Volatile
  private var latest: HistoryReference<T>? = null

  /** Retains the earlier record while two records fit without an index. */
  private var previous: HistoryReference<T>? = null

  /** Indexes three or more records on one branch. */
  private var singleBranch: BranchHistory<T>? = null

  /** Indexes records from different branches by branch identity. */
  private var branches: MutableMap<PMarkerNodeBranch, BranchHistory<T>>? = null

  /** Returns the newest live record visible at [version], or null when no such record remains. */
  fun get(version: PMarkerNodeVersion): T? {
    val recent = latest
    if (recent != null && recent.branch === version.branch && recent.number <= version.number) {
      recent.get()?.let { return it }
    }
    return synchronized(this) {
      if (latest == null && previous == null && singleBranch == null && branches.isNullOrEmpty()) return@synchronized null
      var current: PMarkerNodeVersion? = version
      while (current != null) {
        val branch = current.branch
        val number = current.number
        val indexed = singleBranch?.takeIf { it.branch === branch } ?: branches?.get(branch)
        indexed?.get(number)?.let { return@synchronized it }
        val single = latest
        if (single != null && single.branch === branch && single.number <= number) {
          single.get()?.let { return@synchronized it }
        }
        val earlier = previous
        if (earlier != null && earlier.branch === branch && earlier.number <= number) {
          earlier.get()?.let { return@synchronized it }
        }
        current = branch.parent
      }
      null
    }
  }

  /**
   * Publishes [value] for [version] through a weak reference.
   * Records on each branch must publish in version order. The latest version can receive a replacement.
   * The owning root must keep [value] reachable while that version is in use.
   */
  @Synchronized
  fun put(version: PMarkerNodeVersion, value: T) {
    val reference = HistoryReference(value, this, references, version)
    val histories = branches
    val indexed = singleBranch
    if (histories != null) {
      histories.getOrPut(version.branch) { BranchHistory(version.branch) }.put(reference)
    }
    else if (indexed != null) {
      if (indexed.branch === version.branch) indexed.put(reference)
      else {
        branches = IdentityHashMap<PMarkerNodeBranch, BranchHistory<T>>().apply {
          put(indexed.branch, indexed)
          put(version.branch, BranchHistory<T>(version.branch).apply { put(reference) })
        }
        singleBranch = null
      }
    }
    else {
      val recent = latest?.takeIf { it.get() != null }
      val earlier = previous?.takeIf { it.get() != null }
      val first = earlier ?: recent
      val second = if (earlier != null) recent else null
      previous = null
      if (first != null) {
        if (first.branch === version.branch) {
          check((second ?: first).number <= reference.number)
          if (second == null || second.number == reference.number) {
            if (first.number != reference.number) previous = first
          }
          else {
            singleBranch = BranchHistory<T>(version.branch).apply {
              put(first)
              put(second)
              put(reference)
            }
          }
        }
        else {
          branches = IdentityHashMap<PMarkerNodeBranch, BranchHistory<T>>().apply {
            put(first.branch, BranchHistory<T>(first.branch).apply {
              put(first)
              if (second != null) put(second)
            })
            put(version.branch, BranchHistory<T>(version.branch).apply { put(reference) })
          }
        }
      }
    }
    latest = reference
  }

  /** Cleans collected records without removing a replacement for [reference]. */
  @Synchronized
  private fun remove(reference: HistoryReference<T>) {
    val records = singleBranch?.takeIf { it.branch === reference.branch } ?: branches?.get(reference.branch)
    if (records != null) {
      records.collect()
      if (records.isEmpty) {
        if (singleBranch === records) singleBranch = null
        else branches?.remove(reference.branch)
      }
    }
    if (branches?.isEmpty() == true) branches = null
    if (previous === reference) previous = null
    if (latest === reference) latest = null
  }

  /** Stores records for one branch in version order. The owning history protects all access with its lock. */
  private class BranchHistory<T : Any>(val branch: PMarkerNodeBranch) {
    private val records: BulkArrayQueue<HistoryReference<T>> = BulkArrayQueue(4)
    private var collectedSinceCompaction: Int = 0

    val isEmpty: Boolean
      get() = records.size() == 0

    fun get(number: Long): T? {
      trimHead()
      var index = floorIndex(number)
      while (index >= 0) {
        records.elementAt(index--).get()?.let { return it }
      }
      return null
    }

    fun put(reference: HistoryReference<T>) {
      trimHead()
      val last = records.peekLast()
      check(last == null || last.number <= reference.number)
      if (last?.number == reference.number) records.getAndSetElementAt(records.size() - 1, reference)
      else records.addLast(reference)
    }

    fun collect() {
      collectedSinceCompaction++
      trimHead()
      if (collectedSinceCompaction >= 32 && collectedSinceCompaction >= records.size() / 2) {
        records.removeAll { it.get() == null }
        collectedSinceCompaction = 0
      }
    }

    private fun trimHead() {
      while (true) {
        val first = records.peekFirst() ?: return
        if (first.get() != null) return
        records.pollFirst()
      }
    }

    private fun floorIndex(number: Long): Int {
      var low = 0
      var high = records.size()
      while (low < high) {
        val middle = (low + high) ushr 1
        if (records.elementAt(middle).number <= number) low = middle + 1
        else high = middle
      }
      return low - 1
    }
  }

  /** Identifies a record and its owner for cleanup after collection. */
  private class HistoryReference<T : Any>(
    value: T,
    private val owner: PMarkerNodeHistory<T>,
    queue: ReferenceQueue<Any>,
    version: PMarkerNodeVersion,
  ) : WeakReference<T>(value, queue) {
    val branch: PMarkerNodeBranch = version.branch
    val number: Long = version.number

    /** Starts cleanup of this reference in its history after collection. */
    fun reclaim() {
      owner.remove(this)
    }
  }

  companion object {
    /** Receives collected history values so [collect] can remove their records. */
    private val references: ReferenceQueue<Any> = ReferenceQueue()

    /** Starts the version history of an empty root. */
    val initialVersion: PMarkerNodeVersion = PMarkerNodeVersion(PMarkerNodeBranch(null, 0), 0)

    /**
     * Keeps purge records alive so an older valid state cannot become visible again.
     * Each history record stores the actual purge version separately.
     */
    val purgedState: PMarkerInvalidNode = PMarkerInvalidNode(0, 0)

    /**
     * Extends the branch of [previous] if [previous] is still its tip. Otherwise, starts a new branch at [previous].
     * Each allocation also processes a bounded number of collection notifications.
     */
    fun nextVersion(previous: PMarkerNodeVersion): PMarkerNodeVersion {
      collect(64)
      val number = previous.number + 1
      val branch = if (previous.branch.tip.compareAndSet(previous.number, number)) previous.branch
      else PMarkerNodeBranch(previous, number)
      return PMarkerNodeVersion(branch, number)
    }

    /** Processes at most [limit] collection notifications. */
    private fun collect(limit: Int) {
      repeat(limit) {
        val reference = references.poll() ?: return
        (reference as HistoryReference<*>).reclaim()
      }
    }
  }
}
