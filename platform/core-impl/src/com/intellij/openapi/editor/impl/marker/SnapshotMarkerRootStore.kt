// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.ex.DocumentPatch
import com.intellij.openapi.editor.ex.DocumentSnapshot
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener
import com.intellij.openapi.editor.ex.RangeMarkerEx
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.impl.EditorDocumentPriorities
import com.intellij.util.Processor
import com.intellij.util.containers.HashingStrategy
import com.intellij.util.containers.Java11Shim
import com.intellij.util.containers.ReferenceQueueable
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
 * @param T the marker type in this store
 * @param document supplies snapshot transitions and owns the store registry
 * @param retainStrong retains every marker when true; otherwise each marker selects its ownership
 * @param onMarkersInvalidated receives marker handles that became invalid during a text change
 * @param onDocumentChanged receives the document event after the marker callbacks run
 * @param onMarkersAffected receives handles for valid markers whose policies processed the text change
 */
class SnapshotMarkerRootStore<T : SnapshotRangeMarkerImpl> @JvmOverloads constructor(
  document: DocumentImpl,
  private val markerClass: Class<T>,
  private val retainStrong: Boolean = false,
  private val onMarkersInvalidated: ((List<SnapshotMarkerReference<T>>) -> Unit)? = null,
  private val onDocumentChanged: ((DocumentEvent) -> Unit)? = null,
  private val onMarkersAffected: ((List<SnapshotMarkerReference<T>>) -> Unit)? = null,
  private val emptyRoot: PMarkerRoot = PMarkerRoot.empty(),
) : MarkerRootUpdater() {
  private val documentReference: WeakReference<DocumentImpl> = WeakReference(document)

  override val document: DocumentImpl?
    get() = documentReference.get()

  private val roots: ConcurrentMap<DocumentSnapshot, RootState> = Java11Shim.INSTANCE.createConcurrentWeakMap(HashingStrategy.identity())

  /** The document owns this slot. The store must not retain the document through its current root. */
  internal val currentRootCache: WeakReference<CurrentRootCache> = WeakReference(document.snapshotMarkerStores.register(this))
  private var disposed: Boolean = false

  private val documentListener: PrioritizedDocumentListener? =
    if (onMarkersInvalidated != null || onDocumentChanged != null || onMarkersAffected != null) {
      object : PrioritizedDocumentListener {
        override fun getPriority(): Int = EditorDocumentPriorities.RANGE_MARKER

        /** Calls the marker callbacks before [onDocumentChanged] and releases the notifications even if a callback throws. */
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
    documentListener?.let { listener -> document.addDocumentListener(listener) }
  }

  /** Returns [handles] with this store's marker type, or an empty list when [handles] is `null`. */
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
  }

  /** Returns whether this store has a root entry for this exact [snapshot] instance. */
  fun containsSnapshot(snapshot: DocumentSnapshot): Boolean = roots.containsKey(snapshot)

  /**
   * Returns the root for [snapshot], or `null` if this store has no root for that snapshot.
   * A successful lookup can fill the cache when [snapshot] is current.
   */
  fun root(snapshot: DocumentSnapshot): PMarkerRoot? {
    val cache = currentRootCache.get()
    cache?.get(snapshot)?.let { return it.get() }
    val reference = roots[snapshot]?.rootReference ?: return null
    cacheCurrentRoot(cache, snapshot, reference)
    return reference.get()
  }

  /**
   * Processes valid markers from one captured root by increasing start offset, then marker ID.
   * Usually faster than [processRangeMarkersOverlappingWith]
   * Includes markers beyond the snapshot text length. Purges collected handles and skips disposed markers.
   * Returns `false` when [processor] stops processing.
   */
  fun processAllRangeMarkers(
    snapshot: DocumentSnapshot,
    processor: Processor<in RangeMarkerEx>,
  ): Boolean = processMarkers(snapshot, processor) { root, markerProcessor ->
    root.processAllRangeMarkers(markerProcessor)
  }

  /**
   * Processes valid markers that non-strictly intersect the requested range and contain every bit in [tastePreference].
   * A zero preference matches every marker. Purges collected handles and skips disposed markers.
   * Returns `false` when [processor] stops processing.
   */
  fun processRangeMarkersOverlappingWith(
    snapshot: DocumentSnapshot,
    startOffset: Int,
    endOffset: Int,
    tastePreference: Int,
    processor: Processor<in RangeMarkerEx>,
  ): Boolean = processMarkers(snapshot, processor) { root, markerProcessor ->
    root.processRangeMarkersOverlappingWith(startOffset, endOffset, tastePreference, markerProcessor)
  }

  /** Captures a root and purges collected handles after [query], including when processing stops or throws. */
  private inline fun processMarkers(
    snapshot: DocumentSnapshot,
    processor: Processor<in RangeMarkerEx>,
    query: (PMarkerRoot, MarkerProcessor) -> Boolean,
  ): Boolean {
    SnapshotMarkerReference.processQueue()
    val root = root(snapshot) ?: return true
    val markerProcessor = MarkerProcessor(processor)
    try {
      return query(root, markerProcessor)
    }
    finally {
      markerProcessor.purgeCollected(this, snapshot)
    }
  }

  /** Creates a handle for this store without reading a document snapshot. */
  override fun createHandle(marker: SnapshotRangeMarkerImpl, retainStrong: Boolean): SnapshotMarkerReference<T> =
    SnapshotMarkerReference.create(markerClass.cast(marker), this.retainStrong || retainStrong)

  /**
   * Returns the shared atomic reference for [snapshot], creating a root entry from [initialRoot] when necessary.
   * The existing entry takes precedence over [initialRoot]. A lookup can cache the reference when [snapshot] is current.
   */
  fun rootReference(snapshot: DocumentSnapshot, initialRoot: PMarkerRoot = emptyRoot): AtomicReference<PMarkerRoot> {
    val cache = currentRootCache.get()
    cache?.get(snapshot)?.let { return it }
    processQueue()
    val rootState = rootState(snapshot, initialRoot)
    val reference = rootState.rootReference
    cacheCurrentRoot(cache, snapshot, reference)
    return reference
  }

  /**
   * Fills [cache] only while this store is active and [snapshot] is current, outside publication.
   * The registry's monitor protects the check and update.
   */
  private fun cacheCurrentRoot(cache: CurrentRootCache?, snapshot: DocumentSnapshot, reference: AtomicReference<PMarkerRoot>) {
    val document = documentReference.get()
    if (document != null && cache != null) {
      val markerStores = document.snapshotMarkerStores
      synchronized(markerStores) {
        if (!disposed && !markerStores.isPublishing && document.core.snapshot() === snapshot) {
          cache.state = CurrentRootCache.State(snapshot, reference)
        }
      }
    }
  }

  /** Returns whether this store is active and its document's current snapshot is this exact [snapshot] instance. */
  internal fun isCurrentSnapshot(snapshot: DocumentSnapshot): Boolean = !disposed && documentReference.get()?.core?.snapshot() === snapshot

  /** Returns the root reference for the current document snapshot, creating the root entry when necessary. */
  override fun currentRootReference(): AtomicReference<PMarkerRoot> {
    val document = documentReference.get()
    return rootReference(document!!.core.snapshot())
  }

  /** Publishes updates to the current root through the commit gate. */
  override fun updateCurrentRoot(update: (PMarkerRoot) -> PMarkerRoot): PMarkerRoot? =
    updateCurrentRoot { _, root -> update(root) }

  /**
   * Applies [update] to the root for [snapshot], creating the root entry when necessary.
   * The update retries on concurrent root changes. [update] must have no side effects.
   */
  fun updateRoot(snapshot: DocumentSnapshot, update: (PMarkerRoot) -> PMarkerRoot) {
    updateRootAtomically(rootReference(snapshot), update)
  }

  /**
   * Passes the selected snapshot and root to [update] outside the commit gate.
   * Retries if the snapshot or root changes before publication. [update] must have no side effects.
   *
   * @return the new root, or `null` if the store is disposed or [update] returns the input root
   */
  internal fun updateCurrentRoot(update: (DocumentSnapshot, PMarkerRoot) -> PMarkerRoot): PMarkerRoot? {
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

  /**
   * Removes all state for [handles] from an existing root for [snapshot].
   *
   * @return `true` if this call changes the root
   */
  fun purgeMany(snapshot: DocumentSnapshot, handles: Collection<SnapshotMarkerReference<*>>): Boolean {
    if (handles.isEmpty()) return false
    processQueue()
    val rootReference = roots[snapshot]?.rootReference
    return rootReference != null && updateRootAtomically(rootReference) { it: PMarkerRoot -> it.purgeMany(handles) } != null
  }

  /** Replaces the root for [snapshot] with [root]. */
  internal fun restoreRoot(snapshot: DocumentSnapshot, root: PMarkerRoot) {
    rootReference(snapshot, root).set(root)
  }

  /**
   * Applies [patch] to [beforeRoot] using the text from [beforeSnapshot] and [afterSnapshot].
   * Collects affected and invalidated handles only when the corresponding callbacks exist.
   * [installRoot] stores the returned state, and the document listener delivers its notifications.
   */
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

  /**
   * Installs [state] for [snapshot] if no entry exists.
   *
   * @return the existing state, or [state] if this call installs it
   */
  internal fun installRoot(snapshot: DocumentSnapshot, state: RootState): RootState = roots.putIfAbsent(snapshot, state) ?: state

  /**
   * Installs a merged root for [mergedSnapshot] if that snapshot has no root entry.
   * Keeps the states from [markerSnapshot] and adds valid markers found only in [metadataSnapshot].
   * Uses the available root when only one input snapshot has a root. Creates no entry when neither has a root.
   * The merged state has no pending notifications.
   */
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

  /** Returns the existing state for [snapshot], or atomically creates one from [initialRoot]. */
  private fun rootState(snapshot: DocumentSnapshot, initialRoot: PMarkerRoot): RootState {
    return roots[snapshot] ?: roots.computeIfAbsent(snapshot) { RootState(initialRoot) }
  }

  /**
   * Removes root entries whose snapshot keys were collected.
   *
   * @return `true` when at least one entry was removed
   */
  fun processQueue(): Boolean = (roots as ReferenceQueueable).processQueue()

  private class MarkerProcessor(
    private val processor: Processor<in RangeMarkerEx>,
  ) : Processor<PMarkerRoot.MarkerEntry>, PMarkerRoot.MarkerDeltaProcessor {
    private var collectedHandles: ArrayList<SnapshotMarkerReference<*>>? = null

    override fun process(entry: PMarkerRoot.MarkerEntry): Boolean {
      val marker = entry.marker
      if (marker == null) {
        val handles = collectedHandles ?: ArrayList<SnapshotMarkerReference<*>>().also { collectedHandles = it }
        handles.add(entry.handle)
        return true
      }
      return marker.isDisposed() || processor.process(marker)
    }

    override fun process(entry: PMarkerRoot.MarkerEntry, ancestorDelta: Int): Boolean = process(entry)

    fun purgeCollected(rootStore: SnapshotMarkerRootStore<*>, snapshot: DocumentSnapshot) {
      val handles = collectedHandles ?: return
      rootStore.purgeMany(snapshot, handles)
    }
  }

  /**
   * Registers [store] and holds its current root reference.
   * Publication clears [state] before the snapshot changes. It fills [state] after the new snapshot is visible.
   */
  internal class CurrentRootCache(val store: SnapshotMarkerRootStore<*>) {
    @Volatile
    var reference: AtomicReference<PMarkerRoot>? = null
      private set

    @Volatile
    var state: State? = null
      set(value) {
        reference = value?.reference
        field = value
      }

    /**
     * Captures the root state and root instance for [snapshot] without creating an entry.
     * The caller must hold the registry's monitor.
     */
    fun captureRoot(snapshot: DocumentSnapshot): CapturedRoot = CapturedRoot(this, store.roots[snapshot])

    /** Returns the cached reference for this exact [snapshot] instance, or `null` on a cache miss. */
    fun get(snapshot: DocumentSnapshot): AtomicReference<PMarkerRoot>? {
      val state = state ?: return null
      return if (state.snapshot === snapshot) state.reference else null
    }

    class State(val snapshot: DocumentSnapshot, val reference: AtomicReference<PMarkerRoot>)
  }

  internal class CapturedRoot(
    val cache: CurrentRootCache,
    private val beforeState: RootState?,
  ) {
    val store: SnapshotMarkerRootStore<*>
      get() = cache.store

    val root: PMarkerRoot? = beforeState?.rootReference?.get()
    var afterState: RootState? = null

    /**
     * Copies the captured state with the captured [root] and shares pending notifications.
     *
     * @return the copied state, or `null` if the snapshot had no state
     */
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

    val invalidatedMarkers: List<SnapshotMarkerReference<*>>
      get() = notificationsReference?.get()?.invalidatedMarkers ?: emptyList()

    val affectedMarkers: List<SnapshotMarkerReference<*>>
      get() = notificationsReference?.get()?.affectedMarkers ?: emptyList()

    /**
     * Creates a separate root reference for [root] and shares pending notifications with this state.
     * Releasing either state's notifications clears them for both states.
     */
    fun inherit(root: PMarkerRoot): RootState = RootState(root, notificationsReference)

    /** Clears the pending notifications, including those shared through [inherit]. */
    fun releaseNotifications() {
      notificationsReference?.set(null)
    }

    private class Notifications(
      val invalidatedMarkers: List<SnapshotMarkerReference<*>>,
      val affectedMarkers: List<SnapshotMarkerReference<*>>,
    )
  }
}
