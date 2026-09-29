// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicReference

/**
 * Updates immutable marker roots through atomic references.
 *
 * A subclass selects the reference for the current document state.
 *
 * The supplied update can run more than once after a compare-and-set failure. It must have no side effects.
 *
 * [updateCurrentRoot] returns `true` when it published a new root and `false` when the update returns the current root.
 * [updateRootAtomically] returns the published root or `null` when the update returns the current root.
 */
@ApiStatus.Internal
abstract class MarkerRootUpdater {
  internal fun updateCurrentRoot(update: (PMarkerRoot) -> PMarkerRoot): Boolean = updateRootAtomically(null, update) != null

  internal abstract fun currentRootReference(): AtomicReference<PMarkerRoot>

  internal open fun updateRootAtomically(
    rootReference: AtomicReference<PMarkerRoot>?, // null means currentRootReference()
    update: (PMarkerRoot) -> PMarkerRoot,
  ): PMarkerRoot? {
    while (true) {
      val reference = rootReference ?: currentRootReference()
      val oldRoot = reference.get()
      val newRoot = update(oldRoot)
      if (newRoot === oldRoot) return null
      if (reference.compareAndSet(oldRoot, newRoot)) return newRoot
    }
  }
}
