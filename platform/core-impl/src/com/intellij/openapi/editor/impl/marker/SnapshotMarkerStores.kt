// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import com.intellij.openapi.editor.ex.DocumentOp
import com.intellij.openapi.editor.ex.DocumentPatch
import com.intellij.openapi.editor.ex.DocumentSnapshot
import com.intellij.openapi.editor.impl.marker.SnapshotMarkerRootStore.CapturedRoot
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

/**
 * Applies each [DocumentOp] to all marker roots for one document.
 *
 * The registry holds each active store strongly. This ownership ensures that every active store receives each document operation.
 * A store registers itself during construction. [SnapshotMarkerRootStore.dispose] removes the store from this registry.
 *
 * Its monitor guards validation and publication. Tree transformations run outside the monitor.
 */
@ApiStatus.Internal
class SnapshotMarkerStores {
  private val stores: MutableList<SnapshotMarkerRootStore> = ContainerUtil.createEmptyCOWList()
  private var storeVersion: Long = 0

  internal fun register(store: SnapshotMarkerRootStore) {
    synchronized(this) {
      stores.add(store)
      storeVersion++
    }
  }

  internal fun unregister(store: SnapshotMarkerRootStore) {
    synchronized(this) {
      stores.remove(store)
      storeVersion++
    }
  }

  @TestOnly
  fun applyPatch(beforeSnapshot: DocumentSnapshot, afterSnapshot: DocumentSnapshot, patch: DocumentPatch?) {
    if (afterSnapshot === beforeSnapshot) return
    while (true) {
      if (tryCommit(beforeSnapshot, afterSnapshot, patch, isCurrent = { true }, publish = {})) return
    }
  }

  @TestOnly
  fun applyPatch(beforeSnapshot: DocumentSnapshot, patch: DocumentPatch): DocumentSnapshot {
    var afterSnapshot = beforeSnapshot
    for (op in patch.ops()) {
      afterSnapshot = afterSnapshot.applyOp(op)
    }
    applyPatch(beforeSnapshot, afterSnapshot, patch)
    return afterSnapshot
  }

  internal fun tryCommit(
    beforeSnapshot: DocumentSnapshot,
    afterSnapshot: DocumentSnapshot,
    patch: DocumentPatch?,
    isCurrent: () -> Boolean,
    publish: () -> Unit,
  ): Boolean {
    SnapshotMarkerEngineImpl.processQueue()
    val version: Long
    val capturedRoots: List<CapturedRoot>
    synchronized(this) {
      version = storeVersion
      capturedRoots = if (beforeSnapshot === afterSnapshot) {
        emptyList()
      }
      else {
        stores.map { it.captureRoot(beforeSnapshot) }
      }
    }
    if (patch != null) {
      validatePatch(beforeSnapshot, afterSnapshot, patch)
      for (i in capturedRoots.indices) {
        val it = capturedRoots[i]
        val root = it.root ?: continue
        it.afterState = it.store.preparePatch(root, beforeSnapshot, afterSnapshot, patch)
      }
    }
    else {
      require(beforeSnapshot.text() === afterSnapshot.text()) {
        "Snapshots must share the same text instance, but op corrupted the text"
      }
      for (i in capturedRoots.indices) {
        val captured = capturedRoots[i]
        captured.afterState = captured.inheritState()
      }
    }
    return synchronized(this) {
      if (!isCurrent() || storeVersion != version) return false
      for (i in capturedRoots.indices) {
        val captured = capturedRoots[i]
        if (captured.store.root(beforeSnapshot) !== captured.root) return false
      }
      for (i in capturedRoots.indices) {
        val captured = capturedRoots[i]
        val state = captured.afterState ?: continue
        captured.store.installRoot(afterSnapshot, state)
      }
      publish()
      true
    }
  }

  fun mergeMarkerRoots(markerSnapshot: DocumentSnapshot, metadataSnapshot: DocumentSnapshot): DocumentSnapshot {
    SnapshotMarkerEngineImpl.processQueue()
    if (markerSnapshot === metadataSnapshot) return metadataSnapshot

    val relevantStores = stores.filter { it.containsSnapshot(markerSnapshot) || it.containsSnapshot(metadataSnapshot) }
    if (relevantStores.isEmpty()) {
      return metadataSnapshot
    }

    val mergedSnapshot = metadataSnapshot.newInstance()
    relevantStores.forEach { it.merge(markerSnapshot, metadataSnapshot, mergedSnapshot) }
    return mergedSnapshot
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
}
