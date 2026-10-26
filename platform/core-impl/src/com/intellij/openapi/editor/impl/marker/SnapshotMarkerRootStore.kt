// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.ex.DocumentSnapshot
import com.intellij.openapi.editor.ex.DocumentPatch
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.impl.EditorDocumentPriorities
import com.intellij.util.containers.HashingStrategy
import com.intellij.util.containers.Java11Shim
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

  private val roots: ConcurrentMap<DocumentSnapshot, RootState> = Java11Shim.INSTANCE.createConcurrentWeakMap(HashingStrategy.identity())
  @Volatile
  private var cachedRootReference: CachedRootReference? = null
  private var disposed: Boolean = false

  /** Validates and publishes editor-owned marker changes under the commit gate. */
  internal val currentRootUpdater: MarkerRootUpdater = object : MarkerRootUpdater() {
    override fun currentRootReference(): AtomicReference<PMarkerRoot> = this@SnapshotMarkerRootStore.currentRootReference()

    override fun updateRootAtomically(
      rootReference: AtomicReference<PMarkerRoot>?,
      update: (PMarkerRoot) -> PMarkerRoot,
    ): PMarkerRoot? {
      if (rootReference != null) return super.updateRootAtomically(rootReference, update)
      return updateCurrentRootAtomically(PMarkerRootImpl.empty(), update)
    }
  }

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
    synchronized(markerStores) {
      if (disposed) return
      disposed = true
      markerStores.unregister(this)
    }
    documentListener?.let { listener -> documentReference.get()?.removeDocumentListener(listener) }
    roots.clear()
    cachedRootReference = null
  }

  fun containsSnapshot(snapshot: DocumentSnapshot): Boolean = roots.containsKey(snapshot)

  fun root(snapshot: DocumentSnapshot): PMarkerRoot? = roots[snapshot]?.rootReference?.get()

  @TestOnly
  fun containsMarkerId(snapshot: DocumentSnapshot, markerId: Long): Boolean =
    (root(snapshot) as? PMarkerRootImpl)?.containsMarkerId(markerId) == true

  fun rootReference(snapshot: DocumentSnapshot, initialRoot: PMarkerRoot = PMarkerRootImpl.empty()): AtomicReference<PMarkerRoot> {
    val cached = cachedRootReference
    if (cached != null && cached.get() === snapshot) {
      val reference = cached.reference.get()
      if (reference != null) return reference
    }
    processQueue()
    val reference = rootState(snapshot, initialRoot).rootReference
    cachedRootReference = CachedRootReference(snapshot, reference)
    return reference
  }

  override fun currentRootReference(): AtomicReference<PMarkerRoot> {
    val document = documentReference.get()
    return rootReference(document!!.core.snapshot())
  }

  fun updateRoot(snapshot: DocumentSnapshot, initialRoot: PMarkerRoot = PMarkerRootImpl.empty(), update: (PMarkerRoot) -> PMarkerRoot) {
    updateRootAtomically(rootReference(snapshot, initialRoot), update)
  }

  fun updateCurrentRoot(initialRoot: PMarkerRoot, update: (PMarkerRoot) -> PMarkerRoot): Boolean {
    return updateCurrentRootAtomically(initialRoot, update) != null
  }

  private fun updateCurrentRootAtomically(initialRoot: PMarkerRoot, update: (PMarkerRoot) -> PMarkerRoot): PMarkerRoot? {
    val document = documentReference.get()
    val markerStores = document!!.snapshotMarkerStores
    while (true) {
      processQueue()
      val snapshot: DocumentSnapshot
      val reference: AtomicReference<PMarkerRoot>
      val oldRoot: PMarkerRoot
      synchronized(markerStores) {
        if (disposed) return null
        snapshot = document.core.snapshot()
        reference = rootState(snapshot, initialRoot).rootReference
        oldRoot = reference.get()
      }
      val newRoot = update(oldRoot)
      synchronized(markerStores) {
        if (disposed) return null
        if (document.core.snapshot() === snapshot && reference.compareAndSet(oldRoot, newRoot)) {
          return if (newRoot === oldRoot) null else newRoot
        }
      }
    }
  }

  private fun updateRootIfPresent(snapshot: DocumentSnapshot, update: (PMarkerRoot) -> PMarkerRoot): Boolean {
    processQueue()
    val rootReference = roots[snapshot]?.rootReference ?: return false
    return updateRootAtomically(rootReference, update) != null
  }

  fun purgeMany(snapshot: DocumentSnapshot, markerIds: LongList): Boolean {
    if (markerIds.isEmpty()) return false
    return updateRootIfPresent(snapshot) { it.purgeMany(markerIds) }
  }

  internal fun captureRoot(snapshot: DocumentSnapshot): CapturedRoot {
    return CapturedRoot(this, roots[snapshot])
  }

  internal fun preparePatch(
      beforeRoot: PMarkerRoot,
      beforeSnapshot: DocumentSnapshot,
      afterSnapshot: DocumentSnapshot,
      patch: DocumentPatch,
  ): RootState {
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
    return RootState(afterRoot, invalidatedMarkerIds ?: LongLists.EMPTY_LIST, affectedMarkerIds ?: LongLists.EMPTY_LIST)
  }

  internal fun installRoot(snapshot: DocumentSnapshot, state: RootState) {
    roots.putIfAbsent(snapshot, state)
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
    return roots[snapshot] ?: roots.computeIfAbsent(snapshot) { RootState(initialRoot) }
  }

  /**
   * Removes root entries whose snapshot keys were collected.
   *
   * @return `true` when at least one entry was removed
   */
  fun processQueue(): Boolean = (roots as ReferenceQueueable).processQueue()

  /** Caches the lookup without keeping the snapshot or its marker root alive. */
  private class CachedRootReference(
    snapshot: DocumentSnapshot,
    reference: AtomicReference<PMarkerRoot>,
  ) : WeakReference<DocumentSnapshot>(snapshot) {
    val reference: WeakReference<AtomicReference<PMarkerRoot>> = WeakReference(reference)
  }

  internal class CapturedRoot(
    val store: SnapshotMarkerRootStore,
    private val beforeState: RootState?,
  ) {
    val root: PMarkerRoot? = beforeState?.rootReference?.get()
    var afterState: RootState? = null

    fun inheritState(): RootState? {
      val state = beforeState ?: return null
      return RootState(checkNotNull(root), state.invalidatedMarkerIds, state.affectedMarkerIds)
    }
  }

  internal class RootState(
    root: PMarkerRoot,
    val invalidatedMarkerIds: LongList = LongLists.EMPTY_LIST,
    val affectedMarkerIds: LongList = LongLists.EMPTY_LIST,
  ) {
    val rootReference: AtomicReference<PMarkerRoot> = AtomicReference(root)
  }
}
