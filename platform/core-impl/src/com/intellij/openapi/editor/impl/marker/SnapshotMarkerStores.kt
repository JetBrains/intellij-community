// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import com.intellij.openapi.editor.ex.DocumentOp
import com.intellij.openapi.editor.ex.DocumentPatch
import com.intellij.openapi.editor.ex.DocumentSnapshot
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

/**
 * Applies each [DocumentOp] to all marker roots for one document.
 *
 * The registry holds each active store strongly. This ownership ensures that every active store receives each document operation.
 * A store registers itself during construction. [SnapshotMarkerRootStore.dispose] removes the store from this registry.
 */
@ApiStatus.Internal
class SnapshotMarkerStores {
  private val stores: MutableList<SnapshotMarkerRootStore> = ContainerUtil.createEmptyCOWList()

  internal fun register(store: SnapshotMarkerRootStore) {
    stores.add(store)
  }

  internal fun unregister(store: SnapshotMarkerRootStore) {
    stores.remove(store)
  }

  fun applyPatch(beforeSnapshot: DocumentSnapshot, afterSnapshot: DocumentSnapshot, patch: DocumentPatch?): DocumentSnapshot {
    if (afterSnapshot === beforeSnapshot) return afterSnapshot
    val capturedRoots = captureRoots(beforeSnapshot)
    return additionalApply(patch, beforeSnapshot, afterSnapshot, capturedRoots)
  }

  @TestOnly
  fun applyPatch(beforeSnapshot: DocumentSnapshot, patch: DocumentPatch): DocumentSnapshot {
    var afterSnapshot = beforeSnapshot
    for (op in patch.ops()) {
      afterSnapshot = afterSnapshot.applyOp(op)
    }
    return applyPatch(beforeSnapshot, afterSnapshot, patch)
  }

  private fun additionalApply(
    patch: DocumentPatch?,
    beforeSnapshot: DocumentSnapshot,
    afterSnapshot: DocumentSnapshot,
    capturedRoots: List<CapturedRoot>,
  ): DocumentSnapshot {
    if (patch != null) {
      validatePatch(beforeSnapshot, afterSnapshot, patch)
      for (i in capturedRoots.indices) {
        val it = capturedRoots[i]
        it.store.applyPatch(it.root, beforeSnapshot, afterSnapshot, patch)
      }
    }
    else {
      require(beforeSnapshot.text() === afterSnapshot.text()) {
        "Snapshots must share the same text instance, but op corrupted the text"
      }
      for (i in capturedRoots.indices) {
        val it = capturedRoots[i]
        it.store.inherit(it.root, afterSnapshot)
      }
    }
    return afterSnapshot
  }

  fun mergeMarkerRoots(markerSnapshot: DocumentSnapshot, metadataSnapshot: DocumentSnapshot): DocumentSnapshot {
    SnapshotMarkerEngineImpl.processQueue()
    if (markerSnapshot === metadataSnapshot) return metadataSnapshot

    val relevantStores = stores.filter { it.containsSnapshot(markerSnapshot) || it.containsSnapshot(metadataSnapshot) }
    if (relevantStores.isEmpty()) {
      return metadataSnapshot
    }

    val mergedSnapshot = metadataSnapshot.copyWithNewIdentity()
    relevantStores.forEach { it.merge(markerSnapshot, metadataSnapshot, mergedSnapshot) }
    return mergedSnapshot
  }

  /**
   * Captures the current marker root of each store for [snapshot].
   * If a caller adds a marker to [snapshot] after this call, the next snapshot does not get that marker.
   */
  private fun captureRoots(snapshot: DocumentSnapshot): List<CapturedRoot> {
    SnapshotMarkerEngineImpl.processQueue()
    val result = ArrayList<CapturedRoot>(stores.size)
    stores.forEach { store ->
      val capturedRoot = store.captureRoot(snapshot)?.let { CapturedRoot(store, it) }
      if (capturedRoot != null) {
        result.add(capturedRoot)
      }
    }
    return result
  }

  private fun validatePatch(beforeSnapshot: DocumentSnapshot, afterSnapshot: DocumentSnapshot, patch: DocumentPatch) {
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
