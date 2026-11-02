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
 * Stores a persistent AVL subtree with strong child references and weak parent history.
 * Copies share the marker handle. A snapshot version selects the applicable node and parent through their histories.
 *
 * @property entry the marker state before shifts from ancestors.
 * @property lazyDelta the offset shift still pending for both children. [entry] already includes this shift.
 */
internal class PMarkerNode(
  val version: PMarkerNodeVersion,
  val entry: MarkerEntry,
  val left: PMarkerNode?,
  val right: PMarkerNode?,
  val lazyDelta: Int = 0,
) : PMarkerNodeState {
  val handle: SnapshotMarkerReference<*>
    get() = entry.handle

  /** Finds a parent for a snapshot version without retaining that parent. */
  val parents: PMarkerNodeHistory<PMarkerNode> = PMarkerNodeHistory()
  val height: Int = maxOf(left?.height ?: 0, right?.height ?: 0) + 1

  /** Gives the largest end offset in this subtree, including [lazyDelta] but excluding shifts from ancestors. */
  val maximumEnd: Int = maxOf(entry.nodeEnd, shiftedEnd(left), shiftedEnd(right))

  /** Combines the flavor flags of all subtree entries for query pruning. */
  val flavorFlags: Byte = entry.flavorFlags or (left?.flavorFlags ?: 0) or (right?.flavorFlags ?: 0)

  /** Sums the measures of all subtree entries for prefix queries. */
  val aggregate: Int = entry.measure + (left?.aggregate ?: 0) + (right?.aggregate ?: 0)

  /** Counts entries whose persistent policies can require a full traversal after an edit. */
  val persistentCount: Int = (if (entry.policy.isPersistent) 1 else 0) +
                             (left?.persistentCount ?: 0) + (right?.persistentCount ?: 0)

  private fun shiftedEnd(child: PMarkerNode?): Int = if (child == null) Int.MIN_VALUE else child.maximumEnd + lazyDelta
}

/** Represents a valid, invalid, or removed marker state in a handle's history. */
internal sealed interface PMarkerNodeState

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

  /** Indexes records by branch identity and version number. Allocates the index when a second live record arrives. */
  private var branches: MutableMap<PMarkerNodeBranch, BranchHistory<T>>? = null

  /** Returns the newest live record visible at [version], or null when no such record remains. */
  fun get(version: PMarkerNodeVersion): T? {
    val recent = latest
    if (recent != null && recent.branch === version.branch && recent.number <= version.number) {
      recent.get()?.let { return it }
    }
    return synchronized(this) {
      var current: PMarkerNodeVersion? = version
      while (current != null) {
        val branch = current.branch
        val number = current.number
        branches?.get(branch)?.get(number)?.let { return@synchronized it }
        val single = latest
        if (single != null && single.branch === branch && single.number <= number) {
          single.get()?.let { return@synchronized it }
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
    val previous = latest
    var histories = branches
    if (histories == null && previous?.get() != null) {
      histories = IdentityHashMap()
      histories[previous.branch] = BranchHistory<T>().apply { put(previous) }
      branches = histories
    }
    histories?.getOrPut(version.branch) { BranchHistory() }?.put(reference)
    latest = reference
  }

  /** Cleans collected records without removing a replacement for [reference]. */
  @Synchronized
  private fun remove(reference: HistoryReference<T>) {
    val records = branches?.get(reference.branch)
    if (records != null) {
      records.collect()
      if (records.isEmpty) branches!!.remove(reference.branch)
    }
    if (latest === reference) latest = null
  }

  /** Stores records for one branch in version order. The owning history protects all access with its lock. */
  private class BranchHistory<T : Any> {
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
