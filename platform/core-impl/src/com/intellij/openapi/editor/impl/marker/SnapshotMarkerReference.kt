// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicLong

/** Identifies one marker across snapshots and provides access to it with the selected ownership. */
sealed interface SnapshotMarkerReference<out T : SnapshotRangeMarkerImpl> : Comparable<SnapshotMarkerReference<*>> {
  val order: Long

  /** Returns the marker, or null after a weak marker is collected. */
  fun get(): T?

  override fun compareTo(other: SnapshotMarkerReference<*>): Int = order.compareTo(other.order)

  companion object {
    private val sequence: AtomicLong = AtomicLong()

    /** Creates a handle that retains [marker] when [retainStrong] is true. */
    @JvmStatic
    fun <T : SnapshotRangeMarkerImpl> create(marker: T, retainStrong: Boolean): SnapshotMarkerReference<T> =
      if (retainStrong) StrongSnapshotMarkerReference(marker) else WeakSnapshotMarkerReference(marker)

    internal fun nextOrder(): Long = sequence.incrementAndGet()
  }
}

/** Selects a marker state for a snapshot version without retaining that state. */
internal val SnapshotMarkerReference<*>.states: PMarkerNodeHistory<PMarkerNodeState>
  get() = when (this) {
    is StrongSnapshotMarkerReference<*> -> states
    is WeakSnapshotMarkerReference<*> -> states
  }

/** Retains the marker while keeping only weak references to its states. */
internal class StrongSnapshotMarkerReference<out T : SnapshotRangeMarkerImpl>(private val marker: T) : SnapshotMarkerReference<T> {
  override val order: Long = SnapshotMarkerReference.nextOrder()
  val states: PMarkerNodeHistory<PMarkerNodeState> = PMarkerNodeHistory()

  override fun get(): T = marker
}

/** Queues this reference for cleanup after its marker is collected. */
internal class WeakSnapshotMarkerReference<T : SnapshotRangeMarkerImpl>(marker: T) :
  WeakReference<T>(marker, SnapshotMarkerEngine.markerQueue), SnapshotMarkerReference<T> {
  override val order: Long = SnapshotMarkerReference.nextOrder()
  val states: PMarkerNodeHistory<PMarkerNodeState> = PMarkerNodeHistory()
  internal val rootStorageReference: WeakReference<MarkerRootUpdater> = marker.rootStorage.weakReference

  override fun get(): T? = super.get()
}
