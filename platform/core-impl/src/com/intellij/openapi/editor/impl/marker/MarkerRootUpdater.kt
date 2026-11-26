// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import java.lang.ref.WeakReference

/**
 * Updates immutable marker roots through atomic references.
 *
 * A subclass selects the reference for the current document state.
 */
abstract class MarkerRootUpdater {
  internal val weakReference: WeakReference<MarkerRootUpdater> = WeakReference(this)

  /**
   * Creates a handle for [marker] with this updater's ownership policy.
   * This method does not insert the marker into a root.
   *
   * @param retainStrong requests strong retention of [marker]. The updater can enforce strong retention for all handles.
   */
  internal abstract fun createHandle(marker: SnapshotRangeMarkerImpl, retainStrong: Boolean): SnapshotMarkerReference<*>

  /**
   * Purges collected [handles] from the current root.
   *
   * @return `true` if the purge publishes a new root
   */
  internal fun purgeCollected(handles: Collection<SnapshotMarkerReference<*>>): Boolean =
    updateCurrentRoot { root -> root.purgeMany(handles) } != null

  /**
   * Returns the atomic reference for the current document state.
   * Successive calls can return different references as the document state changes.
   */
  internal abstract fun currentRootReference(): PMarkerRootReference

  /**
   * Applies [update] to the current root with this updater's publication protocol.
   * [update] can run more than once and must have no side effects.
   *
   * @return the root published by this call, or `null` if it publishes no new root
   */
  abstract fun updateCurrentRoot(update: (PMarkerRoot) -> PMarkerRoot): PMarkerRoot?

  /**
   * Applies [update] to [rootReference] with compare-and-set. Every retry uses the supplied reference.
   *
   * [update] can run again after a failed publication and must have no side effects.
   * Returning the input root instance skips publication.
   *
   * @return the root published by this call, or `null` if it publishes no new root
   */
  internal fun updateRootAtomically(
    rootReference: PMarkerRootReference,
    update: (PMarkerRoot) -> PMarkerRoot,
  ): PMarkerRoot? {
    while (true) {
      val oldRoot = rootReference.get()
      val newRoot = update(oldRoot)
      if (newRoot === oldRoot) return null
      if (rootReference.compareAndSet(oldRoot, newRoot)) return newRoot
    }
  }

  /** Runs after each successful write to a [PMarkerRootReference] that this updater owns. */
  internal open fun onRootPublished(rootReference: PMarkerRootReference) {
  }
}
