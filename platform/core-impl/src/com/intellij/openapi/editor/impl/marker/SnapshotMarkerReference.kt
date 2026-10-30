// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import org.jetbrains.annotations.ApiStatus
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicLong

/** Identifies one marker across snapshots and provides access to it with the selected ownership. */
sealed interface SnapshotMarkerReference<out T : SnapshotRangeMarkerImpl?> : Comparable<SnapshotMarkerReference<*>> {
  val order: Long

  fun get(): T

  override fun compareTo(other: SnapshotMarkerReference<*>): Int = order.compareTo(other.order)

  companion object {
    private val sequence: AtomicLong = AtomicLong()

    internal fun nextOrder(): Long = sequence.incrementAndGet()
  }
}

/** Retains the marker for the lifetime of this reference. */
internal open class StrongSnapshotMarkerReference<out T : SnapshotRangeMarkerImpl>(private val marker: T) : SnapshotMarkerReference<T> {
  final override val order: Long = SnapshotMarkerReference.nextOrder()

  final override fun get(): T = marker
}

/** Queues this reference for cleanup after its marker is collected. */
internal open class WeakSnapshotMarkerReference<T : SnapshotRangeMarkerImpl>(marker: T) :
  WeakReference<T>(marker, SnapshotMarkerEngineImpl.markerQueue), SnapshotMarkerReference<T?> {
  final override val order: Long = SnapshotMarkerReference.nextOrder()
  internal val rootStorageReference: WeakReference<MarkerRootUpdater> = marker.rootStorage.weakReference

  final override fun get(): T? = super.get()
}
