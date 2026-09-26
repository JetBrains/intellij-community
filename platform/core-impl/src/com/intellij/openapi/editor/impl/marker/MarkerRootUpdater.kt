// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicReference

/**
 * Updates immutable marker roots through atomic references.
 *
 * A subclass selects the reference for the current document state. It can override [withRootUpdateLock] when selection and replacement need one lock.
 *
 * The supplied update can run more than once after a compare-and-set failure. It must have no side effects.
 *
 * [updateCurrentRoot] and [updateRoot] return `true` when they publish a new root. They return `false` when the update returns the current root.
 */
@ApiStatus.Internal
abstract class MarkerRootUpdater {
  internal fun currentRootReference(): AtomicReference<PMarkerRoot> = selectCurrentRootReference()

  internal fun updateCurrentRoot(update: (PMarkerRoot) -> PMarkerRoot): Boolean = withRootUpdateLock {
    updateRootAtomically(selectCurrentRootReference(), update)
  }

  internal fun updateRoot(
    rootReference: AtomicReference<PMarkerRoot>,
    update: (PMarkerRoot) -> PMarkerRoot,
  ): Boolean = withRootUpdateLock {
    updateRootAtomically(rootReference, update)
  }

  protected abstract fun selectCurrentRootReference(): AtomicReference<PMarkerRoot>

  protected open fun <T> withRootUpdateLock(action: () -> T): T = action()

  private fun updateRootAtomically(
    rootReference: AtomicReference<PMarkerRoot>,
    update: (PMarkerRoot) -> PMarkerRoot,
  ): Boolean {
    while (true) {
      val oldRoot = rootReference.get()
      val newRoot = update(oldRoot)
      if (newRoot === oldRoot) return false
      if (rootReference.compareAndSet(oldRoot, newRoot)) return true
    }
  }
}
