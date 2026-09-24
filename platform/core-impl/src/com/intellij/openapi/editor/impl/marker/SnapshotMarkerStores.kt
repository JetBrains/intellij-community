// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import com.intellij.openapi.editor.ex.DocumentOp
import com.intellij.openapi.editor.ex.DocumentSnapshot
import com.intellij.openapi.editor.ex.DocumentTextPatch
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap

/**
 * Applies each [DocumentOp] to all marker roots for one document.
 *
 * The registry holds each active store strongly. This ownership ensures that every active store receives each document operation.
 * A store registers itself during construction. [SnapshotMarkerRootStore.dispose] removes the store from this registry.
 */
@ApiStatus.Internal
class SnapshotMarkerStores {
  private val stores: MutableSet<SnapshotMarkerRootStore> = ConcurrentHashMap.newKeySet()

  internal fun register(store: SnapshotMarkerRootStore) {
    stores.add(store)
  }

  internal fun unregister(store: SnapshotMarkerRootStore) {
    stores.remove(store)
  }

  fun applyOp(beforeSnapshot: DocumentSnapshot, op: DocumentOp): DocumentSnapshot {
    val capturedRoots = captureRoots(beforeSnapshot)
    val afterSnapshot = beforeSnapshot.applyOp(op)
    if (afterSnapshot === beforeSnapshot) return afterSnapshot

    return additionalApply(op, beforeSnapshot, afterSnapshot, capturedRoots)
  }

  private fun additionalApply(
    op: DocumentOp,
    beforeSnapshot: DocumentSnapshot,
    afterSnapshot: DocumentSnapshot,
    capturedRoots: List<CapturedRoot>,
  ): DocumentSnapshot {
    if (op is DocumentTextPatch) {
      validatePatch(beforeSnapshot, afterSnapshot, op)
      capturedRoots.forEach { it.store.applyPatch(it.root, beforeSnapshot, afterSnapshot, op) }
    }
    else {
      require(beforeSnapshot.text() === afterSnapshot.text()) {
        "Snapshots must share the same text instance, but op: $op corrupted the text"
      }
      capturedRoots.forEach { it.store.inherit(it.root, afterSnapshot) }
    }
    return afterSnapshot
  }

  fun mergeMarkerRoots(markerSnapshot: DocumentSnapshot, metadataSnapshot: DocumentSnapshot): DocumentSnapshot {
    SnapshotMarkerEngineImpl.processQueue()
    if (markerSnapshot === metadataSnapshot) return metadataSnapshot

    val stores = aliveStores().filter { it.containsSnapshot(markerSnapshot) || it.containsSnapshot(metadataSnapshot) }
    if (stores.isEmpty()) return metadataSnapshot

    val mergedSnapshot = metadataSnapshot.copyWithNewIdentity()
    stores.forEach { it.merge(markerSnapshot, metadataSnapshot, mergedSnapshot) }
    return mergedSnapshot
  }

  /**
   * Captures immutable roots before [applyOp] creates the next snapshot.
   * This boundary prevents a marker created during the operation from entering the new snapshot.
   */
  private fun captureRoots(snapshot: DocumentSnapshot): List<CapturedRoot> {
    SnapshotMarkerEngineImpl.processQueue()
    return aliveStores().mapNotNull { store ->
      store.captureRoot(snapshot)?.let { CapturedRoot(store, it) }
    }
  }

  private fun aliveStores(): List<SnapshotMarkerRootStore> = stores.toList()

  private fun validatePatch(beforeSnapshot: DocumentSnapshot, afterSnapshot: DocumentSnapshot, patch: DocumentTextPatch) {
    val beforeLength = beforeSnapshot.text().length()
    val afterLength = afterSnapshot.text().length()
    val startOffset = patch.startOffset()
    val endOffset = patch.endOffset()
    require(startOffset >= 0) { "DocumentTextPatch startOffset must be non-negative" }
    require(endOffset >= startOffset) { "DocumentTextPatch endOffset must not precede startOffset" }
    require(endOffset <= beforeLength) { "DocumentTextPatch range exceeds before snapshot length" }
    val expectedLength = beforeLength.toLong() - (endOffset - startOffset) + patch.newFragment().length
    require(expectedLength == afterLength.toLong()) {
      "After snapshot length is inconsistent with DocumentTextPatch"
    }
  }

  private class CapturedRoot(
    val store: SnapshotMarkerRootStore,
    val root: PMarkerRoot,
  )
}
