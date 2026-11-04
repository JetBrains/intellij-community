// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicLong

/** Identifies one marker across snapshots and provides access to it with the selected ownership. */
sealed interface SnapshotMarkerReference<out T : SnapshotRangeMarkerImpl> : Comparable<SnapshotMarkerReference<*>> {
  val id: Long

  /** Returns the marker, or null after a weak marker is collected. */
  fun get(): T?

  override fun compareTo(other: SnapshotMarkerReference<*>): Int = id.compareTo(other.id)

  companion object {
    private val sequence: AtomicLong = AtomicLong()
    internal val markerQueue: ReferenceQueue<SnapshotRangeMarkerImpl> = ReferenceQueue()

    /** Creates a handle that retains [marker] when [retainStrong] is true. */
    @JvmStatic
    fun <T : SnapshotRangeMarkerImpl> create(marker: T, retainStrong: Boolean): SnapshotMarkerReference<T> =
      if (retainStrong) StrongSnapshotMarkerReference(marker) else WeakSnapshotMarkerReference(marker)

    /**
     * Purges collected weak marker handles from their current roots.
     *
     * @return `true` when at least one root changed
     */
    @JvmStatic
    fun processQueue(): Boolean {
      var reference = markerQueue.poll() as? WeakSnapshotMarkerReference<*> ?: return false
      var batchStorage: MarkerRootUpdater? = null
      val handles = ArrayList<SnapshotMarkerReference<*>>()
      var purgedAny = false
      while (true) {
        val rootStorage = reference.rootStorageReference.get()
        if (rootStorage != null) {
          if (rootStorage !== batchStorage) {
            if (batchStorage != null && batchStorage.purgeCollected(handles)) purgedAny = true
            handles.clear()
            batchStorage = rootStorage
          }
          handles.add(reference)
        }
        reference = markerQueue.poll() as? WeakSnapshotMarkerReference<*> ?: break
      }
      if (batchStorage != null && batchStorage.purgeCollected(handles)) purgedAny = true
      return purgedAny
    }

    internal fun nextId(): Long = sequence.incrementAndGet()
  }
}

/** Selects a marker position or invalid state without retaining it. */
internal val SnapshotMarkerReference<*>.states: PMarkerNodeHistory<PMarkerNodeState>
  get() = when (this) {
    is StrongSnapshotMarkerReference<*> -> states
    is WeakSnapshotMarkerReference<*> -> states
  }

/** Retains the marker while keeping only weak references to its states. */
internal class StrongSnapshotMarkerReference<out T : SnapshotRangeMarkerImpl>(private val marker: T) : SnapshotMarkerReference<T> {
  override val id: Long = SnapshotMarkerReference.nextId()
  val states: PMarkerNodeHistory<PMarkerNodeState> = PMarkerNodeHistory()

  override fun get(): T = marker
}

/** Queues this reference for cleanup after its marker is collected. */
internal class WeakSnapshotMarkerReference<T : SnapshotRangeMarkerImpl>(marker: T) :
  WeakReference<T>(marker, SnapshotMarkerReference.markerQueue), SnapshotMarkerReference<T> {
  override val id: Long = SnapshotMarkerReference.nextId()
  val states: PMarkerNodeHistory<PMarkerNodeState> = PMarkerNodeHistory()
  internal val rootStorageReference: WeakReference<MarkerRootUpdater> = marker.rootStorage.weakReference

  override fun get(): T? = super.get()
}
