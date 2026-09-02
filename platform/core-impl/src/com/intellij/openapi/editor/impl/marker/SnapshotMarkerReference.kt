// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import org.jetbrains.annotations.ApiStatus
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference

/** Provides access to a snapshot marker with weak or strong ownership. */
@ApiStatus.Internal
interface SnapshotMarkerReference {
  fun get(): SnapshotRangeMarkerImpl?
}

internal open class WeakSnapshotMarkerReference(
  marker: SnapshotRangeMarkerImpl,
  queue: ReferenceQueue<in SnapshotRangeMarkerImpl>? = null,
) : WeakReference<SnapshotRangeMarkerImpl>(marker, queue), SnapshotMarkerReference {
  override fun get(): SnapshotRangeMarkerImpl? = super.get()
}

internal class StrongSnapshotMarkerReference(
  private val marker: SnapshotRangeMarkerImpl,
) : SnapshotMarkerReference {
  override fun get(): SnapshotRangeMarkerImpl = marker
}
