// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ex.RangeMarkerEx
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.TextRangeScalarUtil
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater
import java.util.concurrent.atomic.AtomicReference

/**
 * A range marker whose offsets are stored in immutable snapshot roots.
 *
 * Creation and removal update the root for the current snapshot. Existing descendant snapshots stay unchanged.
 * A child snapshot inherits its parent's current root. Atomic root operations coordinate marker changes and child creation.
 */
open class SnapshotRangeMarkerImpl private constructor(
  private val documentOrFile: Any,
  internal val rootStorage: MarkerRootUpdater,
  /** Controls the marker boundaries. See [MarkerSpec]. */
  @Volatile
  private var spec: Byte,
  initialRange: Long,
  retainStrong: Boolean,
) : UserDataHolderBase(), RangeMarkerEx {
  /** Reused for every root operation, including offset reads. */
  val markerHandle: SnapshotMarkerReference<*> = rootStorage.createHandle(this, retainStrong)

  /** Shares the document's current reference without retaining a root from an earlier snapshot. */
  private val currentRootCache: SnapshotMarkerRootStore.CurrentRootCache? =
    if (documentOrFile is DocumentImpl && rootStorage is SnapshotMarkerRootStore<*>) rootStorage.currentRootCache.get() else null

  /**
   * Creates a marker in the range marker store for [document]. A non-null [fileRoot] stores it in that root instead.
   */
  internal constructor(
    document: DocumentImpl,
    fileRoot: VirtualFileMarkerRoot?,
    initialSpec: Byte,
    initialRange: Long,
    retainStrong: Boolean = false,
  ) : this(
    fileRoot?.file ?: document, fileRoot ?: document.rangeMarkers.rootStore(),
    initialSpec, initialRange, retainStrong,
  )

  /** Creates a marker in [fileRoot] before the document is available. */
  internal constructor(
    fileRoot: VirtualFileMarkerRoot,
    initialSpec: Byte,
    initialRange: Long,
  ) : this(fileRoot.file, fileRoot, initialSpec, initialRange, false)

  /**
   * Creates a marker in the supplied [rootStore]. Use this constructor for markers that an editor or a markup model owns.
   */
  protected constructor(
    document: Document,
    rootStore: SnapshotMarkerRootStore<*>,
    initialSpec: Byte,
    initialRange: Long,
  ) : this(
    documentOrFile = document,
    rootStorage = rootStore,
    spec = initialSpec,
    initialRange = initialRange,
    retainStrong = false,
  )

  @Volatile
  private var disposalState: Int = ACTIVE

  @Volatile
  internal var fallbackRange: Long = initialRange
    private set

  fun isDisposed(): Boolean = disposalState == DISPOSED

  @Synchronized
  fun markDisposed() {
    if (isDisposed()) return
    fallbackRange = currentRange()
    disposalState = DISPOSED
  }

  override fun getId(): Long {
    return markerHandle.id
  }

  override fun getDocument(): Document {
    val documentOrFile = documentOrFile
    return if (documentOrFile is VirtualFile) {
      checkNotNull(FileDocumentManager.getInstance().getDocument(documentOrFile)) {
        "Document is unavailable for $documentOrFile"
      }
    }
    else {
      documentOrFile as Document
    }
  }

  override fun getStartOffset(): Int = PMarkerResolution.startOffset(currentRange())

  override fun getEndOffset(): Int = PMarkerResolution.endOffset(currentRange())

  override fun getTextRange(): TextRange = PMarkerResolution.toTextRange(currentRange())

  override fun isValid(): Boolean {
    return !isDisposed() && isValidInRoot(currentRootReference().get()) &&
           (documentOrFile is Document || (documentOrFile as VirtualFile).isValid)
  }

  protected open fun isValidInRoot(root: PMarkerRoot): Boolean = root.isValid(markerHandle)

  override fun isGreedyToLeft(): Boolean = MarkerSpec.isGreedyToLeft(spec)

  override fun isGreedyToRight(): Boolean = MarkerSpec.isGreedyToRight(spec)

  override fun setGreedyToLeft(greedy: Boolean) {
    updateSpec { MarkerSpec.withGreedyToLeft(it, greedy) }
  }

  override fun setGreedyToRight(greedy: Boolean) {
    updateSpec { MarkerSpec.withGreedyToRight(it, greedy) }
  }

  override fun setStickingToRight(value: Boolean) {
    updateSpec { MarkerSpec.withStickyToRight(it, value) }
  }

  fun isStickingToRight(): Boolean = MarkerSpec.isStickingToRight(spec)

  override fun dispose() {
    if (DISPOSAL_STATE_UPDATER.compareAndSet(this, ACTIVE, DISPOSING)) {
      try {
        beforeDispose()
        markDisposed()
        removeFromCurrentRoot()
        afterDispose()
      }
      finally {
        DISPOSAL_STATE_UPDATER.compareAndSet(this, DISPOSING, ACTIVE)
      }
    }
  }

  protected open fun beforeDispose() {
  }

  protected open fun afterDispose() {
  }

  /** Disposes this marker and returns whether this call removes it from the current root. */
  fun removeFromCurrentRoot(): Boolean {
    SnapshotMarkerReference.processQueue()
    val handle = markerHandle
    markDisposed()
    return updateCurrentRoot { it.remove(handle) }
  }

  /**
   * Returns this marker's state in [root], or [PMarkerResolution.ABSENT] when the marker is absent.
   * Marks a retained state as invalid when this marker is disposed.
   */
  fun resolve(root: PMarkerRoot): Long {
    val resolution = root.resolve(markerHandle)
    return if (isDisposed()) {
      PMarkerResolution.invalid(resolution)
    }
    else {
      resolution
    }
  }

  /**
   * Replaces this marker's specification in the current snapshot without changing its ID or range.
   */
  private inline fun updateSpec(transform: (Byte) -> Byte) {
    synchronized(this) {
      if (isDisposed()) return

      val oldSpec = spec
      val newSpec = transform(oldSpec)
      if (newSpec == oldSpec) return

      if (!updateCurrentRoot { root -> if (isDisposed()) root else root.updateSpec(markerHandle, newSpec) }) return
      spec = newSpec
    }
  }

  /** Publishes the identity and offsets together without retaining the root. */
  @Volatile
  private var cachedResolution: CachedResolution? = null

  private fun currentRange(): Long {
    val resolution = currentResolution()
    return if (PMarkerResolution.isAbsent(resolution)) fallbackRange else resolution
  }

  internal fun cacheResolution(root: PMarkerRoot, resolution: Long) {
    cachedResolution = CachedResolution(root.resolutionCacheIdentity, resolution)
  }

  /**
   * Resolves this marker in the current root. Reuses cached offsets for the same identity or advances them across one direct edit.
   * Traverses the tree when neither shortcut applies.
   */
  private fun currentResolution(): Long {
    val root = currentRootReference().get()
    val cacheIdentity = root.resolutionCacheIdentity
    val cached = cachedResolution
    if (cached != null && cached.identity === cacheIdentity) {
      return cached.resolution
    }
    val advanced = if (cached != null && !isDisposed()) {
      root.advanceResolution(cached.identity, cached.resolution)
    }
    else {
      PMarkerResolution.UNRESOLVED
    }
    val resolution = if (advanced != PMarkerResolution.UNRESOLVED) advanced
                            else resolve(root)
    cacheResolution(root, resolution)
    return resolution
  }

  internal fun updateCurrentRoot(update: (PMarkerRoot) -> PMarkerRoot): Boolean {
    return rootStorage.updateCurrentRoot(update) != null
  }

  fun currentRootReference(): AtomicReference<PMarkerRoot> = currentRootCache?.state?.reference ?: rootStorage.currentRootReference()

  override fun toString(): String = "SnapshotRangeMarker(id=$id" +
                                    (if (isDisposed()) ", disposed" else "") +
                                    ")"

  private class CachedResolution(val identity: Any, val resolution: Long)

  companion object {
    /**
     * Creates a marker in the current snapshot and retries if a text change publishes another snapshot.
     * Existing descendant snapshots stay unchanged. Future children inherit the marker.
     *
     * The offsets must satisfy `0 <= startOffset <= endOffset <= document.textLength`.
     *
     * @param spec the boundary flags from [MarkerSpec]
     * @param retainStrong true when the root must retain the marker
     * @param policy defines how document edits transform the marker
     */
    @JvmStatic
    fun createRangeMarker(
      document: Document,
      startOffset: Int,
      endOffset: Int,
      spec: Byte,
      retainStrong: Boolean = false,
      policy: MarkerPolicy = DefaultMarkerPolicy,
    ): SnapshotRangeMarkerImpl {
      return createRangeMarker(document, startOffset, endOffset, spec, policy) { fileRoot ->
        val initialRange = TextRangeScalarUtil.toScalarRange(startOffset, endOffset)
        SnapshotRangeMarkerImpl(document as DocumentImpl, fileRoot, spec, initialRange, retainStrong)
      }
    }

    /**
     * Creates a marker in the current snapshot and retries when a text change publishes another snapshot.
     *
     * @param markerFactory receives the file root.
     * The file root is `null` when the document has no associated file or [FileDocumentManager] is unavailable.
     */
    internal fun <T : SnapshotRangeMarkerImpl> createRangeMarker(
      document: Document,
      startOffset: Int,
      endOffset: Int,
      spec: Byte,
      policy: MarkerPolicy,
      markerFactory: (VirtualFileMarkerRoot?) -> T,
    ): T {
      SnapshotMarkerReference.processQueue()
      val documentImpl = document as DocumentImpl
      val rootStore = documentImpl.rangeMarkers.rootStore()
      require(startOffset >= 0) { "startOffset must be non-negative" }
      require(endOffset >= startOffset) { "endOffset must not precede startOffset" }

      val fileRoot = VirtualFileMarkerRoot.getOrCreate(documentImpl)

      val marker = markerFactory(fileRoot)
      val insertedRoot = checkNotNull(rootStore.updateCurrentRoot { currentSnapshot, root ->
        require(endOffset <= currentSnapshot.text().length()) { "Marker range exceeds snapshot length" }
        root.insert(marker.markerHandle, startOffset, endOffset, spec, marker.flavorFlags, policy = policy)
      })
      marker.cacheResolution(insertedRoot, TextRangeScalarUtil.toScalarRange(startOffset, endOffset))
      return marker
    }

    @JvmStatic
    fun createRangeMarkerForVirtualFile(
      file: VirtualFile,
      startOffset: Int,
      startLine: Int,
      startColumn: Int,
      endLine: Int,
      endColumn: Int,
      persistent: Boolean,
    ): RangeMarkerEx {
      SnapshotMarkerReference.processQueue()
      require(startOffset >= 0) { "startOffset must be non-negative" }

      val cachedDocument = FileDocumentManager.getInstance().getCachedDocument(file) as? DocumentImpl
      val fileRoot = VirtualFileMarkerRoot.getOrCreate(file)
      val spec = MarkerSpec.create(
        isGreedyToLeft = false,
        isGreedyToRight = false,
      )
      val policy = if (persistent) PersistentMarkerPolicy else DefaultMarkerPolicy
      val initialLineColumns = if (persistent && cachedDocument == null &&
                                   startLine >= 0 && startColumn >= 0 && endLine >= 0 && endColumn >= 0) {
        SnapshotLazyRangeMarker.LineColumns(startLine, startColumn, endLine, endColumn)
      }
      else {
        null
      }
      val initialRange = TextRangeScalarUtil.toScalarRange(startOffset, startOffset)
      val marker = SnapshotLazyRangeMarker(fileRoot, spec, initialRange, initialLineColumns)
      val insertedRoot = checkNotNull(fileRoot.updateCurrentRoot { it: PMarkerRoot ->
        it.insert(marker.markerHandle, startOffset, startOffset, spec, marker.flavorFlags, policy = policy)
      })
      marker.cacheResolution(insertedRoot, initialRange)
      return marker
    }

    private const val ACTIVE: Int = 0
    private const val DISPOSING: Int = 1
    private const val DISPOSED: Int = 2
    private val DISPOSAL_STATE_UPDATER: AtomicIntegerFieldUpdater<SnapshotRangeMarkerImpl> =
      AtomicIntegerFieldUpdater.newUpdater(SnapshotRangeMarkerImpl::class.java, "disposalState")
  }
}
