// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.ex.DocumentSnapshot
import com.intellij.openapi.editor.ex.DocumentTextPatch
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.impl.EditorDocumentPriorities
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.containers.ReferenceQueueable
import it.unimi.dsi.fastutil.longs.LongArrayList
import it.unimi.dsi.fastutil.longs.LongList
import it.unimi.dsi.fastutil.longs.LongLists
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicReference
import java.util.function.LongConsumer

/**
 * Stores marker [roots] outside snapshots and follows each [DocumentSnapshot] transition for [document].
 *
 * The store uses weak identity keys. A snapshot can be collected after all other owners release it.
 *
 * The constructor registers this store with the document's [SnapshotMarkerStores].
 * An owner with a shorter lifetime than the document must call [dispose].
 *
 * @param document supplies snapshot transitions and owns the store registry
 * @param onMarkersInvalidated receives marker IDs that became invalid during a text change
 * @param onDocumentChanged receives the document event after the marker callbacks run
 * @param onMarkersAffected receives IDs for valid markers whose policies processed the text change
 */
@ApiStatus.Internal
class SnapshotMarkerRootStore @JvmOverloads constructor(
  document: DocumentImpl,
  private val onMarkersInvalidated: ((LongList) -> Unit)? = null,
  private val onDocumentChanged: ((DocumentEvent) -> Unit)? = null,
  private val onMarkersAffected: ((LongList) -> Unit)? = null,
) : MarkerRootUpdater() {
  private val documentReference: WeakReference<DocumentImpl> = WeakReference(document)

  private val roots: ConcurrentMap<DocumentSnapshot, RootState> =
    CollectionFactory.createConcurrentWeakIdentityMap()

  private val documentListener: PrioritizedDocumentListener? =
    if (onMarkersInvalidated != null || onDocumentChanged != null || onMarkersAffected != null) {
    object : PrioritizedDocumentListener {
      override fun getPriority(): Int = EditorDocumentPriorities.RANGE_MARKER

      override fun documentChanged(event: DocumentEvent) {
        val currentSnapshot = (event.document as DocumentImpl).core.snapshot()
        val state = roots[currentSnapshot]
        onMarkersInvalidated?.invoke(state?.invalidatedMarkerIds ?: LongLists.EMPTY_LIST)
        onMarkersAffected?.invoke(state?.affectedMarkerIds ?: LongLists.EMPTY_LIST)
        onDocumentChanged?.invoke(event)
      }
    }
  }
  else {
    null
  }

  init {
    document.snapshotMarkerStores.register(this)
    documentListener?.let { listener -> document.addDocumentListener(listener) }
  }

  /** Removes the document listener and the store registration. It also clears all snapshot roots. */
  fun dispose(markerStores: SnapshotMarkerStores) {
    processQueue()
    documentListener?.let { listener -> documentReference.get()?.removeDocumentListener(listener) }
    markerStores.unregister(this)
    roots.clear()
  }

  fun containsSnapshot(snapshot: DocumentSnapshot): Boolean = roots.containsKey(snapshot)

  fun root(snapshot: DocumentSnapshot): PMarkerRoot? = roots[snapshot]?.rootReference?.get()

  @TestOnly
  fun containsMarkerId(snapshot: DocumentSnapshot, markerId: Long): Boolean =
    (root(snapshot) as? PMarkerRootImpl)?.containsMarkerId(markerId) == true

  fun rootReference(snapshot: DocumentSnapshot, initialRoot: PMarkerRoot = PMarkerRootImpl.empty()): AtomicReference<PMarkerRoot> {
    return rootState(snapshot, initialRoot).rootReference
  }

  override fun selectCurrentRootReference(): AtomicReference<PMarkerRoot> {
    val document = checkNotNull(documentReference.get()) { "The document is unavailable" }
    return rootReference(document.core.snapshot())
  }

  fun updateRoot(snapshot: DocumentSnapshot, initialRoot: PMarkerRoot = PMarkerRootImpl.empty(), update: (PMarkerRoot) -> PMarkerRoot): Boolean {
    return updateRoot(rootReference(snapshot, initialRoot), update)
  }

  private fun updateRootIfPresent(snapshot: DocumentSnapshot, update: (PMarkerRoot) -> PMarkerRoot): Boolean {
    processQueue()
    val rootReference = roots[snapshot]?.rootReference ?: return false
    return updateRoot(rootReference, update)
  }

  fun purge(snapshot: DocumentSnapshot, markerId: Long): Boolean {
    return updateRootIfPresent(snapshot) { it.purge(markerId) }
  }

  internal fun captureRoot(snapshot: DocumentSnapshot): PMarkerRoot? {
    return roots[snapshot]?.rootReference?.get()
  }

  internal fun applyPatch(
    beforeRoot: PMarkerRoot,
    beforeSnapshot: DocumentSnapshot,
    afterSnapshot: DocumentSnapshot,
    patch: DocumentTextPatch,
  ) {
    processQueue()
    val invalidatedMarkerIds: LongList? = if (onMarkersInvalidated == null) null else LongArrayList()
    val invalidatedMarkerConsumer = if (invalidatedMarkerIds == null) {
      PMarkerRoot.EMPTY_LONG_CONSUMER
    }
    else {
      LongConsumer { invalidatedMarkerIds.add(it) }
    }
    val affectedMarkerIds: LongArrayList? = if (onMarkersAffected == null) null else LongArrayList()
    val affectedMarkerConsumer = if (affectedMarkerIds == null) {
      PMarkerRoot.EMPTY_LONG_CONSUMER
    }
    else {
      val affectedMarkerIdSet = LongOpenHashSet()
      LongConsumer { markerId ->
        if (affectedMarkerIdSet.add(markerId)) affectedMarkerIds.add(markerId)
      }
    }
    val afterRoot = beforeRoot.applyPatch(
      patch,
      beforeSnapshot.text(),
      afterSnapshot.text(),
      invalidatedMarkerConsumer,
      affectedMarkerConsumer,
    )
    val newState = RootState(afterRoot, invalidatedMarkerIds ?: LongLists.EMPTY_LIST, affectedMarkerIds ?: LongLists.EMPTY_LIST)
    roots.putIfAbsent(afterSnapshot, newState)
  }

  internal fun inherit(beforeRoot: PMarkerRoot, afterSnapshot: DocumentSnapshot) {
    processQueue()
    roots.putIfAbsent(afterSnapshot, RootState(beforeRoot))
  }

  internal fun merge(markerSnapshot: DocumentSnapshot, metadataSnapshot: DocumentSnapshot, mergedSnapshot: DocumentSnapshot) {
    processQueue()
    val markerRoots = roots[markerSnapshot]
    val metadataRoots = roots[metadataSnapshot]
    if (markerRoots == null && metadataRoots == null) return

    val mergedRoot = when {
      markerRoots == null -> checkNotNull(metadataRoots).rootReference.get()
      metadataRoots == null -> markerRoots.rootReference.get()
      else -> markerRoots.rootReference.get().mergeValidMarkersFrom(metadataRoots.rootReference.get())
    }
    val newState = RootState(mergedRoot)
    roots.putIfAbsent(mergedSnapshot, newState)
  }

  private fun rootState(snapshot: DocumentSnapshot, initialRoot: PMarkerRoot): RootState {
    processQueue()
    return roots.computeIfAbsent(snapshot) { RootState(initialRoot) }
  }

  /**
   * Removes root entries whose snapshot keys were collected.
   *
   * @return `true` when at least one entry was removed
   */
  fun processQueue(): Boolean = (roots as ReferenceQueueable).processQueue()

  private class RootState(
    root: PMarkerRoot,
    val invalidatedMarkerIds: LongList = LongLists.EMPTY_LIST,
    val affectedMarkerIds: LongList = LongLists.EMPTY_LIST,
  ) {
    val rootReference: AtomicReference<PMarkerRoot> = AtomicReference(root)
  }
}
