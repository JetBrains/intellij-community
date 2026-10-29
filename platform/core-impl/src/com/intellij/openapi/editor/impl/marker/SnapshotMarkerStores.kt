// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import com.intellij.openapi.editor.ex.DocumentOp
import com.intellij.openapi.editor.ex.DocumentPatch
import com.intellij.openapi.editor.ex.DocumentSnapshot
import com.intellij.openapi.editor.impl.marker.SnapshotMarkerRootStore.CapturedRoot
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.IdentityHashMap

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
  private val stores: MutableList<SnapshotMarkerRootStore<*>> = ContainerUtil.createEmptyCOWList()
  private val currentRoots: MutableMap<SnapshotMarkerRootStore<*>, SnapshotMarkerRootStore.CurrentRootCache> = IdentityHashMap()
  private var storeVersion: Long = 0

  /** The commit gate guards this flag. A lookup must not fill a slot while publication changes the current snapshot. */
  internal var isPublishing: Boolean = false
    private set

  /**
   * Registers [store] for document changes and returns an empty cache for its current root.
   * The registry holds both strongly until [unregister] removes them.
   */
  internal fun register(store: SnapshotMarkerRootStore<*>): SnapshotMarkerRootStore.CurrentRootCache =
    synchronized(this) {
      stores.add(store)
      storeVersion++
      SnapshotMarkerRootStore.CurrentRootCache().also { currentRoots[store] = it }
    }

  /**
   * Removes [store] and clears its cache for the current root under this registry's monitor.
   * Commit attempts that captured the previous registration state must retry.
   */
  internal fun unregister(store: SnapshotMarkerRootStore<*>) {
    synchronized(this) {
      stores.remove(store)
      currentRoots.remove(store)?.state = null
      storeVersion++
    }
  }

  /**
   * Updates registered roots from [beforeSnapshot] to [afterSnapshot] for tests, with retries when a commit fails.
   * A `null` [patch] carries roots and pending notifications forward. Both snapshots must then share the same text instance.
   * Identical snapshot instances need no update. This method leaves publication of the document snapshot to the caller.
   */
  @TestOnly
  fun applyPatch(beforeSnapshot: DocumentSnapshot, afterSnapshot: DocumentSnapshot, patch: DocumentPatch?) {
    if (afterSnapshot === beforeSnapshot) return
    while (true) {
      if (tryCommit(beforeSnapshot, afterSnapshot, patch, isCurrent = { true }, publish = {})) return
    }
  }

  /** Applies [patch] to [beforeSnapshot], updates all registered roots, and returns the resulting snapshot. */
  @TestOnly
  fun applyPatch(beforeSnapshot: DocumentSnapshot, patch: DocumentPatch): DocumentSnapshot {
    var afterSnapshot = beforeSnapshot
    for (op in patch.ops()) {
      afterSnapshot = afterSnapshot.applyOp(op)
    }
    applyPatch(beforeSnapshot, afterSnapshot, patch)
    return afterSnapshot
  }

  /**
   * Prepares roots for [afterSnapshot] and tries to publish the snapshot.
   *
   * Captures roots under this registry's monitor and transforms them outside it.
   * Under the monitor, it checks [isCurrent], the registered stores, and the captured roots before installing roots and calling [publish].
   * It clears the caches for current roots before [publish] and fills them afterward for stores whose current snapshot is [afterSnapshot].
   * A `null` [patch] preserves roots and pending notifications. Both snapshots must then share the same text instance.
   *
   * @param isCurrent checks whether the expected document state is still current; runs under this registry's monitor
   * @param publish makes [afterSnapshot] current after the roots are installed; runs under this registry's monitor
   * @return `true` after [publish] returns; `false` if the document state, registrations, or captured roots changed
   * @throws IllegalArgumentException if the patch range or resulting text length is invalid, or a `null` patch changes the text instance
   */
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
        captured.afterState = captured.store.installRoot(afterSnapshot, state)
      }
      isPublishing = true
      for (cache in currentRoots.values) cache.state = null
      try {
        publish()
      }
      finally {
        isPublishing = false
      }
      for (i in capturedRoots.indices) {
        val captured = capturedRoots[i]
        val state = captured.afterState ?: continue
        if (captured.store.isCurrentSnapshot(afterSnapshot)) {
          currentRoots[captured.store]?.state = SnapshotMarkerRootStore.CurrentRootCache.State(afterSnapshot, state.rootReference)
        }
      }
      true
    }
  }

  /**
   * Returns a snapshot with metadata from [metadataSnapshot] and marker states from both snapshots.
   * Each store keeps the states from [markerSnapshot] and adds valid markers found only in [metadataSnapshot].
   * A store with a root for only one input snapshot keeps that root.
   *
   * @return [metadataSnapshot] if the snapshots are identical or no store contains either snapshot; otherwise, a new snapshot
   */
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

  /**
   * Checks the range of [patch] against [beforeSnapshot] and the resulting text length against [afterSnapshot].
   *
   * @throws IllegalArgumentException if the range is invalid or the resulting length differs
   */
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
