// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.ex.DocumentPatch
import com.intellij.openapi.editor.ex.DocumentSnapshot
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.impl.EditorDocumentPriorities
import com.intellij.util.containers.HashingStrategy
import com.intellij.util.containers.Java11Shim
import com.intellij.util.containers.ReferenceQueueable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer

/**
 * Stores marker [roots] outside snapshots and follows each [DocumentSnapshot] transition for [document].
 *
 * The store uses weak identity keys. A snapshot can be collected after all other owners release it.
 *
 * The constructor registers this store with the document's [SnapshotMarkerStores].
 * An owner with a shorter lifetime than the document must call [dispose].
 *
 * @param T the marker type and nullability of all references in this store
 * @param document supplies snapshot transitions and owns the store registry
 * @param onMarkersInvalidated receives marker handles that became invalid during a text change
 * @param onDocumentChanged receives the document event after the marker callbacks run
 * @param onMarkersAffected receives handles for valid markers whose policies processed the text change
 */
@ApiStatus.Internal
class SnapshotMarkerRootStore<T : SnapshotRangeMarkerImpl?> private constructor(
  document: DocumentImpl,
  private val onMarkersInvalidated: ((List<SnapshotMarkerReference<T>>) -> Unit)?,
  private val onDocumentChanged: ((DocumentEvent) -> Unit)?,
  private val onMarkersAffected: ((List<SnapshotMarkerReference<T>>) -> Unit)?,
  @Volatile private var emptyRoot: PMarkerRoot,
  private val referenceFactory: (PMarkerRoot, SnapshotRangeMarkerImpl, Boolean) -> SnapshotMarkerReference<T>,
) : MarkerRootUpdater() {
  private val documentReference: WeakReference<DocumentImpl> = WeakReference(document)

  override val document: DocumentImpl?
    get() = documentReference.get()

  private val roots: ConcurrentMap<DocumentSnapshot, RootState> = Java11Shim.INSTANCE.createConcurrentWeakMap(HashingStrategy.identity())

  @Volatile
  private var cachedRootReference: CachedRootReference? = null
  private var disposed: Boolean = false

  private val documentListener: PrioritizedDocumentListener? =
    if (onMarkersInvalidated != null || onDocumentChanged != null || onMarkersAffected != null) {
      object : PrioritizedDocumentListener {
        override fun getPriority(): Int = EditorDocumentPriorities.RANGE_MARKER

        override fun documentChanged(event: DocumentEvent) {
          val currentSnapshot = (event.document as DocumentImpl).core.snapshot()
          val state = roots[currentSnapshot]
          try {
            onMarkersInvalidated?.invoke(markerReferences(state?.invalidatedMarkers))
            onMarkersAffected?.invoke(markerReferences(state?.affectedMarkers))
            onDocumentChanged?.invoke(event)
          }
          finally {
            state?.releaseNotifications()
          }
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

  @Suppress("UNCHECKED_CAST")
  private fun markerReferences(handles: List<SnapshotMarkerReference<*>>?): List<SnapshotMarkerReference<T>> =
    handles as List<SnapshotMarkerReference<T>>? ?: emptyList()

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

  /** Creates a handle in this store's family without reading a document snapshot. */
  override fun createHandle(marker: SnapshotRangeMarkerImpl, retainStrong: Boolean): SnapshotMarkerReference<T> =
    referenceFactory(emptyRoot, marker, retainStrong)

  @TestOnly
  fun containsMarker(snapshot: DocumentSnapshot, handle: SnapshotMarkerReference<*>): Boolean =
    root(snapshot)?.containsMarker(handle) == true

  fun rootReference(snapshot: DocumentSnapshot, initialRoot: PMarkerRoot = emptyRoot): AtomicReference<PMarkerRoot> {
    val cached = cachedRootReference
    if (cached != null && cached.get() === snapshot) {
      val reference = cached.reference.get()
      if (reference != null) return reference
    }
    processQueue()
    val rootState = rootState(snapshot, initialRoot)
    val reference = rootState.rootReference
    cachedRootReference = CachedRootReference(snapshot, rootState)
    return reference
  }

  override fun currentRootReference(): AtomicReference<PMarkerRoot> {
    val document = documentReference.get()
    return rootReference(document!!.core.snapshot())
  }

  /** Uses the commit gate when [rootReference] is `null`. */
  override fun updateRootAtomically(
    rootReference: AtomicReference<PMarkerRoot>?,
    update: (PMarkerRoot) -> PMarkerRoot,
  ): PMarkerRoot? {
    if (rootReference != null) return super.updateRootAtomically(rootReference, update)
    return updateCurrentRootAtomically { _, root -> update(root) }
  }

  fun updateRoot(snapshot: DocumentSnapshot, update: (PMarkerRoot) -> PMarkerRoot) {
    updateRootAtomically(rootReference(snapshot), update)
  }

  /** Passes the selected snapshot to [update] and retries if that snapshot changes before publication. */
  internal fun updateCurrentRootAtomically(update: (DocumentSnapshot, PMarkerRoot) -> PMarkerRoot): PMarkerRoot? {
    val initialRoot = emptyRoot
    val document = documentReference.get()
    val markerStores = document!!.snapshotMarkerStores
    while (true) {
      processQueue()
      val snapshot = document.core.snapshot()
      val reference: AtomicReference<PMarkerRoot>
      val oldRoot: PMarkerRoot
      synchronized(markerStores) {
        if (disposed) return null
        reference = rootState(snapshot, initialRoot).rootReference
        oldRoot = reference.get()
      }
      val newRoot = update(snapshot, oldRoot)
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

  fun purgeMany(snapshot: DocumentSnapshot, handles: Collection<SnapshotMarkerReference<*>>): Boolean {
    if (handles.isEmpty()) return false
    return updateRootIfPresent(snapshot) { it.purgeMany(handles) }
  }

  internal fun restoreRoot(snapshot: DocumentSnapshot, root: PMarkerRoot) {
    emptyRoot = root.emptyRoot()
    rootReference(snapshot, root).set(root)
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
    val invalidatedMarkers: ArrayList<SnapshotMarkerReference<*>>? = if (onMarkersInvalidated == null) null else ArrayList()
    val invalidatedMarkerConsumer = if (invalidatedMarkers == null) {
      PMarkerRoot.EMPTY_HANDLE_CONSUMER
    }
    else {
      Consumer<SnapshotMarkerReference<*>> { invalidatedMarkers.add(it) }
    }
    val affectedMarkers: ArrayList<SnapshotMarkerReference<*>>? = if (onMarkersAffected == null) null else ArrayList()
    val affectedMarkerConsumer = if (affectedMarkers == null) {
      PMarkerRoot.EMPTY_HANDLE_CONSUMER
    }
    else {
      val affectedHandles = HashSet<SnapshotMarkerReference<*>>()
      Consumer<SnapshotMarkerReference<*>> { handle ->
        if (affectedHandles.add(handle)) affectedMarkers.add(handle)
      }
    }
    val afterRoot = beforeRoot.applyPatch(
      patch,
      beforeSnapshot.text(),
      afterSnapshot.text(),
      invalidatedMarkerConsumer,
      affectedMarkerConsumer,
    )
    return RootState(afterRoot, invalidatedMarkers ?: emptyList(), affectedMarkers ?: emptyList())
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
    rootState: RootState,
  ) : WeakReference<DocumentSnapshot>(snapshot) {
    val reference: WeakReference<AtomicReference<PMarkerRoot>> = rootState.weakRootReference
  }

  internal class CapturedRoot(
    val store: SnapshotMarkerRootStore<*>,
    private val beforeState: RootState?,
  ) {
    val root: PMarkerRoot? = beforeState?.rootReference?.get()
    var afterState: RootState? = null

    fun inheritState(): RootState? {
      val state = beforeState ?: return null
      return state.inherit(checkNotNull(root))
    }
  }

  internal class RootState private constructor(
    root: PMarkerRoot,
    private val notificationsReference: AtomicReference<Notifications?>?,
  ) {
    constructor(
      root: PMarkerRoot,
      invalidatedMarkers: List<SnapshotMarkerReference<*>> = emptyList(),
      affectedMarkers: List<SnapshotMarkerReference<*>> = emptyList(),
    ) : this(
      root,
      if (invalidatedMarkers.isEmpty() && affectedMarkers.isEmpty()) null
      else AtomicReference<Notifications?>(Notifications(invalidatedMarkers, affectedMarkers)),
    )

    val rootReference: AtomicReference<PMarkerRoot> = AtomicReference(root)
    val weakRootReference: WeakReference<AtomicReference<PMarkerRoot>> = WeakReference(rootReference)

    val invalidatedMarkers: List<SnapshotMarkerReference<*>>
      get() = notificationsReference?.get()?.invalidatedMarkers ?: emptyList()

    val affectedMarkers: List<SnapshotMarkerReference<*>>
      get() = notificationsReference?.get()?.affectedMarkers ?: emptyList()

    fun inherit(root: PMarkerRoot): RootState = RootState(root, notificationsReference)

    fun releaseNotifications() {
      notificationsReference?.set(null)
    }

    private class Notifications(
      val invalidatedMarkers: List<SnapshotMarkerReference<*>>,
      val affectedMarkers: List<SnapshotMarkerReference<*>>,
    )
  }

  companion object {
    /** Creates a store for markers with mixed ownership. */
    @JvmStatic
    @JvmOverloads
    fun create(
      document: DocumentImpl,
      onMarkersInvalidated: ((List<SnapshotMarkerReference<SnapshotRangeMarkerImpl?>>) -> Unit)? = null,
      onDocumentChanged: ((DocumentEvent) -> Unit)? = null,
      onMarkersAffected: ((List<SnapshotMarkerReference<SnapshotRangeMarkerImpl?>>) -> Unit)? = null,
      emptyRoot: PMarkerRoot = PMarkerRoot.empty(),
    ): SnapshotMarkerRootStore<SnapshotRangeMarkerImpl?> = SnapshotMarkerRootStore(
      document, onMarkersInvalidated, onDocumentChanged, onMarkersAffected, emptyRoot,
    ) { root, marker, retainStrong -> root.createHandle(marker, retainStrong) }

    /** Creates a store for [markerClass] with ownership selected by each marker. */
    fun <T : SnapshotRangeMarkerImpl> create(
      document: DocumentImpl,
      markerClass: Class<T>,
      onMarkersInvalidated: ((List<SnapshotMarkerReference<T?>>) -> Unit)? = null,
      onDocumentChanged: ((DocumentEvent) -> Unit)? = null,
      onMarkersAffected: ((List<SnapshotMarkerReference<T?>>) -> Unit)? = null,
      emptyRoot: PMarkerRoot = PMarkerRoot.empty(),
    ): SnapshotMarkerRootStore<T?> = SnapshotMarkerRootStore(document, onMarkersInvalidated, onDocumentChanged, onMarkersAffected, emptyRoot,
    ) { root, marker, retainStrong -> root.createHandle(markerClass.cast(marker), retainStrong) }

    /** Creates a store that retains each marker of [markerClass]. */
    fun <T : SnapshotRangeMarkerImpl> strong(
      document: DocumentImpl,
      markerClass: Class<T>,
      onMarkersInvalidated: ((List<SnapshotMarkerReference<T>>) -> Unit)? = null,
      onDocumentChanged: ((DocumentEvent) -> Unit)? = null,
      onMarkersAffected: ((List<SnapshotMarkerReference<T>>) -> Unit)? = null,
      emptyRoot: PMarkerRoot = PMarkerRoot.empty(),
    ): SnapshotMarkerRootStore<T> = SnapshotMarkerRootStore(
      document, onMarkersInvalidated, onDocumentChanged, onMarkersAffected, emptyRoot,
    ) { root, marker, _ -> root.createStrongHandle(markerClass.cast(marker)) }
  }
}
