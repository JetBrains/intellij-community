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
 * Stores marker [roots] outside the [document] snapshot and follows each [DocumentSnapshot] transition.
 */
@ApiStatus.Internal
class SnapshotMarkerRootStore @JvmOverloads constructor(
  document: DocumentImpl,
  private val onMarkersInvalidated: ((LongList) -> Unit)? = null,
  private val onDocumentChanged: ((DocumentEvent) -> Unit)? = null,
  private val onMarkersAffected: ((LongList) -> Unit)? = null,
) {
  private val documentReference: WeakReference<DocumentImpl> = WeakReference(document)

  private val roots: ConcurrentMap<DocumentSnapshot, RootState> =
    CollectionFactory.createConcurrentWeakIdentityMap { _, _, rootState -> rootState?.clean() }

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

  fun dispose(markerStores: SnapshotMarkerStores) {
    processQueue()
    documentListener?.let { listener -> documentReference.get()?.removeDocumentListener(listener) }
    markerStores.unregister(this)
    roots.values.forEach(RootState::clean)
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

  internal fun retainedRootReference(snapshot: DocumentSnapshot): AtomicReference<PMarkerRoot> {
    val rootState = rootState(snapshot, PMarkerRootImpl.empty())
    rootState.retainAfterSnapshotCollection()
    return rootState.rootReference
  }

  private fun updateRoot(rootReference: AtomicReference<PMarkerRoot>, update: (PMarkerRoot) -> PMarkerRoot): Boolean {
    while (true) {
      val oldRoot = rootReference.get()
      val newRoot = update(oldRoot)
      if (newRoot === oldRoot) return false
      if (rootReference.compareAndSet(oldRoot, newRoot)) return true
    }
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
    val existingState = roots.putIfAbsent(afterSnapshot, newState)
    if (existingState != null) {
      newState.clean()
    }
  }

  internal fun inherit(beforeRoot: PMarkerRoot, afterSnapshot: DocumentSnapshot) {
    processQueue()
    val newState = RootState(beforeRoot)
    val existingState = roots.putIfAbsent(afterSnapshot, newState)
    if (existingState != null) {
      newState.clean()
    }
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
    val existingState = roots.putIfAbsent(mergedSnapshot, newState)
    if (existingState != null) {
      newState.clean()
    }
  }

  private fun rootState(snapshot: DocumentSnapshot, initialRoot: PMarkerRoot): RootState {
    processQueue()
    return roots.computeIfAbsent(snapshot) { RootState(initialRoot) }
  }

  fun processQueue(): Boolean = (roots as ReferenceQueueable).processQueue()

  private class RootState(
    root: PMarkerRoot,
    val invalidatedMarkerIds: LongList = LongLists.EMPTY_LIST,
    val affectedMarkerIds: LongList = LongLists.EMPTY_LIST,
  ) {
    val rootReference: AtomicReference<PMarkerRoot> = AtomicReference(root)

    @Volatile
    private var retainRoot: Boolean = false

    fun clean() {
      if (!retainRoot) {
        rootReference.set(rootReference.get().emptyRoot())
      }
    }

    fun retainAfterSnapshotCollection() {
      retainRoot = true
    }
  }
}
