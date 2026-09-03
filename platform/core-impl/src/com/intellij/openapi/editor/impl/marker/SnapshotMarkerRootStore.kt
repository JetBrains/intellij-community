// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.ex.DocumentSnapshot
import com.intellij.openapi.editor.ex.DocumentTextPatch
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.impl.EditorDocumentPriorities
import com.intellij.util.containers.CollectionFactory
import it.unimi.dsi.fastutil.longs.LongArrayList
import it.unimi.dsi.fastutil.longs.LongList
import it.unimi.dsi.fastutil.longs.LongLists
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicReference
import java.util.function.LongConsumer

/**
 * Stores marker roots outside a document snapshot and follows each snapshot transition.
 */
@ApiStatus.Internal
class SnapshotMarkerRootStore @JvmOverloads constructor(
  private val document: DocumentImpl,
  private val emptyRoot: PMarkerRoot = PMarkerRootImpl.empty(),
  private val onMarkersInvalidated: ((LongList) -> Unit)? = null,
  private val onDocumentChanged: ((DocumentEvent) -> Unit)? = null,
) {
  private val roots: ConcurrentMap<DocumentSnapshot, RootState> = CollectionFactory.createConcurrentWeakIdentityMap()

  private val documentListener: PrioritizedDocumentListener? = if (onMarkersInvalidated != null || onDocumentChanged != null) {
    object : PrioritizedDocumentListener {
      override fun getPriority(): Int = EditorDocumentPriorities.RANGE_MARKER

      override fun documentChanged(event: DocumentEvent) {
        onMarkersInvalidated?.invoke(roots[document.core.snapshot()]?.invalidatedMarkerIds ?: LongLists.EMPTY_LIST)
        onDocumentChanged?.invoke(event)
      }
    }
  }
  else {
    null
  }

  init {
    SnapshotMarkerEngineImpl.registerRootStore(this)
    documentListener?.let(document::addDocumentListener)
  }

  fun dispose() {
    documentListener?.let(document::removeDocumentListener)
    SnapshotMarkerEngineImpl.unregisterRootStore(this)
    roots.clear()
  }

  fun containsSnapshot(snapshot: DocumentSnapshot): Boolean = roots.containsKey(snapshot)

  fun root(snapshot: DocumentSnapshot): PMarkerRoot? = roots[snapshot]?.rootReference?.get()

  fun rootReference(snapshot: DocumentSnapshot): AtomicReference<PMarkerRoot> {
    return roots.computeIfAbsent(snapshot) { RootState(emptyRoot) }.rootReference
  }

  fun updateRoot(snapshot: DocumentSnapshot, update: (PMarkerRoot) -> PMarkerRoot): Boolean {
    return updateRoot(rootReference(snapshot), update)
  }

  private fun updateRootIfPresent(snapshot: DocumentSnapshot, update: (PMarkerRoot) -> PMarkerRoot): Boolean {
    val rootReference = roots[snapshot]?.rootReference ?: return false
    return updateRoot(rootReference, update)
  }

  fun purge(snapshot: DocumentSnapshot, markerId: Long): Boolean {
    return updateRootIfPresent(snapshot) { it.purge(markerId) }
  }

  internal fun applyPatch(beforeSnapshot: DocumentSnapshot, afterSnapshot: DocumentSnapshot, patch: DocumentTextPatch) {
    val beforeRoots = roots[beforeSnapshot] ?: return
    val invalidatedMarkerIds: LongList? = if (onMarkersInvalidated == null) null else LongArrayList()
    val invalidatedMarkerConsumer = if (invalidatedMarkerIds == null) {
      PMarkerRoot.EMPTY_LONG_CONSUMER
    }
    else {
      LongConsumer { invalidatedMarkerIds.add(it) }
    }
    val afterRoot = beforeRoots.rootReference.get().applyPatch(
      patch,
      beforeSnapshot.text(),
      afterSnapshot.text(),
      invalidatedMarkerConsumer,
    )
    roots.putIfAbsent(afterSnapshot, RootState(afterRoot, invalidatedMarkerIds ?: LongLists.EMPTY_LIST))
  }

  internal fun inherit(beforeSnapshot: DocumentSnapshot, afterSnapshot: DocumentSnapshot) {
    val beforeRoots = roots[beforeSnapshot] ?: return
    roots.putIfAbsent(afterSnapshot, RootState(beforeRoots.rootReference.get()))
  }

  internal fun merge(markerSnapshot: DocumentSnapshot, metadataSnapshot: DocumentSnapshot, mergedSnapshot: DocumentSnapshot) {
    val markerRoots = roots[markerSnapshot]
    val metadataRoots = roots[metadataSnapshot]
    if (markerRoots == null && metadataRoots == null) return

    val mergedRoot = when {
      markerRoots == null -> checkNotNull(metadataRoots).rootReference.get()
      metadataRoots == null -> markerRoots.rootReference.get()
      else -> markerRoots.rootReference.get().mergeValidMarkersFrom(metadataRoots.rootReference.get())
    }
    roots.putIfAbsent(mergedSnapshot, RootState(mergedRoot))
  }

  private fun updateRoot(rootReference: AtomicReference<PMarkerRoot>, update: (PMarkerRoot) -> PMarkerRoot): Boolean {
    while (true) {
      val oldRoot = rootReference.get()
      val newRoot = update(oldRoot)
      if (newRoot === oldRoot) return false
      if (rootReference.compareAndSet(oldRoot, newRoot)) return true
    }
  }

  private class RootState(
    root: PMarkerRoot,
    val invalidatedMarkerIds: LongList = LongLists.EMPTY_LIST,
  ) {
    val rootReference = AtomicReference(root)
  }
}
