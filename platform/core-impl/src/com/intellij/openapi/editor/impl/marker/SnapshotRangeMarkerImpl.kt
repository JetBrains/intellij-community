// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ex.RangeMarkerEx
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater
import java.util.concurrent.atomic.AtomicReference

/**
 * Ordinary range marker whose offsets are stored in the snapshot marker engine.
 */
@ApiStatus.Internal
open class SnapshotRangeMarkerImpl private constructor(
  private val documentOrFile: Any,
  internal val rootStorage: MarkerRootUpdater,
  /** Controls the marker boundaries. See [MarkerSpec]. */
  @Volatile
  private var spec: Byte,
  initialRange: Long,
  retainStrong: Boolean,
) : UserDataHolderBase(), SnapshotMarker, RangeMarkerEx {
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
    return markerHandle.order
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
        SnapshotMarkerEngineImpl.removeRangeMarker(this)
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
                            else SnapshotMarkerEngineImpl.resolveRangeMarker(this, root)
    cacheResolution(root, resolution)
    return resolution
  }

  internal fun updateCurrentRoot(update: (PMarkerRoot) -> PMarkerRoot): Boolean {
    return rootStorage.updateCurrentRoot(update)
  }

  fun currentRootReference(): AtomicReference<PMarkerRoot> = currentRootCache?.state?.reference ?: rootStorage.currentRootReference()

  override fun toString(): String = "SnapshotRangeMarker(id=$id" +
                                    (if (isDisposed()) ", disposed" else "") +
                                    ")"

  private class CachedResolution(val identity: Any, val resolution: Long)

  private companion object {
    private const val ACTIVE: Int = 0
    private const val DISPOSING: Int = 1
    private const val DISPOSED: Int = 2
    private val DISPOSAL_STATE_UPDATER: AtomicIntegerFieldUpdater<SnapshotRangeMarkerImpl> =
      AtomicIntegerFieldUpdater.newUpdater(SnapshotRangeMarkerImpl::class.java, "disposalState")
  }
}
