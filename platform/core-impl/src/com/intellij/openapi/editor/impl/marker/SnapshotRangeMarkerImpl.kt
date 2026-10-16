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
  internal val fileRoot: VirtualFileMarkerRoot?,
  private val rootStorage: MarkerRootUpdater,
  internal val markerId: Long,
  initialSpec: MarkerSpec,
  internal val initialRange: TextRange,
) : UserDataHolderBase(), SnapshotMarker, RangeMarkerEx {
  /**
   * Creates a marker in the range marker store for [document]. A non-null [fileRoot] stores it in that root instead.
   */
  internal constructor(
    document: DocumentImpl,
    fileRoot: VirtualFileMarkerRoot?,
    markerId: Long,
    initialSpec: MarkerSpec,
    initialRange: TextRange,
  ) : this(fileRoot?.file ?: document, fileRoot, fileRoot ?: document.rangeMarkers.rootStore(), markerId, initialSpec, initialRange)

  /** Creates a marker in [fileRoot] before the document is available. */
  internal constructor(
    fileRoot: VirtualFileMarkerRoot,
    markerId: Long,
    initialSpec: MarkerSpec,
    initialRange: TextRange,
  ) : this(fileRoot.file, fileRoot, fileRoot, markerId, initialSpec, initialRange)

  /**
   * Creates a marker in the supplied [rootStore]. Use this constructor for markers that an editor or a markup model owns.
   */
  protected constructor(
    document: Document,
    rootStore: SnapshotMarkerRootStore,
    markerId: Long,
    initialSpec: MarkerSpec,
    initialRange: TextRange,
  ) : this(document, null, rootStore.currentRootUpdater, markerId, initialSpec, initialRange)

  @Volatile
  private var disposalState: Int = ACTIVE

  internal val disposed: Boolean
    get() = disposalState == DISPOSED

  internal fun markDisposed() {
    disposalState = DISPOSED
  }

  @Volatile
  private var spec: MarkerSpec = initialSpec

  override fun getId(): Long {
    return markerId
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

  override fun getStartOffset(): Int = currentResolution().startOffset

  override fun getEndOffset(): Int = currentResolution().endOffset

  override fun getTextRange(): TextRange = currentResolution()

  override fun isValid(): Boolean {
    return !disposed && currentResolution().isValid && (documentOrFile is Document || (documentOrFile as VirtualFile).isValid)
  }

  override fun isGreedyToLeft(): Boolean = spec.isGreedyToLeft

  override fun isGreedyToRight(): Boolean = spec.isGreedyToRight

  override fun setGreedyToLeft(greedy: Boolean) {
    updateSpec { it.withGreedyToLeft(greedy) }
  }

  override fun setGreedyToRight(greedy: Boolean) {
    updateSpec { it.withGreedyToRight(greedy) }
  }

  override fun setStickingToRight(value: Boolean) {
    updateSpec { it.withStickyToRight(value) }
  }

  fun isStickingToRight(): Boolean = spec.isStickingToRight

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
  private fun updateSpec(transform: (MarkerSpec) -> MarkerSpec) {
    synchronized(this) {
      if (disposed) return

      val oldSpec = spec
      val newSpec = transform(oldSpec)
      if (newSpec == oldSpec) return

      if (!updateCurrentRoot { root -> if (disposed) root else root.updateSpec(markerId, newSpec) }) return
      spec = newSpec
    }
  }

  private data class CachedResolution(val cacheIdentity: Any, val resolution: PMarkerResolution)

  private var cachedResolution: CachedResolution? = null

  internal fun cacheResolution(root: PMarkerRoot, resolution: PMarkerResolution) {
    cachedResolution = CachedResolution(cacheIdentity(root), resolution)
  }

  private fun currentResolution(): PMarkerResolution {
    val root = currentRootReference().get()
    val cacheIdentity = cacheIdentity(root)
    val cached = cachedResolution
    if (cached != null && cached.cacheIdentity === cacheIdentity) {
      return cached.resolution
    }
    val resolution = SnapshotMarkerEngineImpl.resolveRangeMarker(this, root)
    cachedResolution = CachedResolution(cacheIdentity, resolution)
    return resolution
  }

  private fun cacheIdentity(root: PMarkerRoot): Any = (root as? PMarkerRootImpl)?.resolutionCacheIdentity ?: root

  internal fun updateCurrentRoot(update: (PMarkerRoot) -> PMarkerRoot): Boolean {
    return rootStorage.updateCurrentRoot(update)
  }

  fun currentRootReference(): AtomicReference<PMarkerRoot> = rootStorage.currentRootReference()

  override fun toString(): String = "SnapshotRangeMarker(id=$markerId" +
                                    (if (disposed) ", disposed" else "") +
                                    ")"

  private companion object {
    private const val ACTIVE: Int = 0
    private const val DISPOSING: Int = 1
    private const val DISPOSED: Int = 2
    private val DISPOSAL_STATE_UPDATER: AtomicIntegerFieldUpdater<SnapshotRangeMarkerImpl> =
      AtomicIntegerFieldUpdater.newUpdater(SnapshotRangeMarkerImpl::class.java, "disposalState")
  }
}
