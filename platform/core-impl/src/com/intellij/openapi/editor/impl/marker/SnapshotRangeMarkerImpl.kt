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
import java.util.concurrent.atomic.AtomicLongFieldUpdater
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
  @Volatile
  private var spec: Byte, /** see [MarkerSpec]*/
  initialRange: Long,
  /** Defines how document edits transform this marker. */
  val policy: MarkerPolicy,
) : UserDataHolderBase(), SnapshotMarker, RangeMarkerEx {
  /**
   * Creates a marker in the range marker store for [document]. A non-null [fileRoot] stores it in that root instead.
   */
  internal constructor(
    document: DocumentImpl,
    fileRoot: VirtualFileMarkerRoot?,
    markerId: Long,
    initialSpec: Byte,
    initialRange: Long,
    policy: MarkerPolicy = DefaultMarkerPolicy,
  ) : this(
    fileRoot?.file ?: document, fileRoot, fileRoot ?: document.rangeMarkers.rootStore(), markerId, initialSpec, initialRange, policy,
  )

  /** Creates a marker in [fileRoot] before the document is available. */
  internal constructor(
    fileRoot: VirtualFileMarkerRoot,
    markerId: Long,
    initialSpec: Byte,
    initialRange: Long,
    policy: MarkerPolicy = DefaultMarkerPolicy,
  ) : this(fileRoot.file, fileRoot, fileRoot, markerId, initialSpec, initialRange, policy)

  /**
   * Creates a marker in the supplied [rootStore]. Use this constructor for markers that an editor or a markup model owns.
   */
  protected constructor(
    document: Document,
    rootStore: SnapshotMarkerRootStore,
    markerId: Long,
    initialSpec: Byte,
    initialRange: Long,
    policy: MarkerPolicy = DefaultMarkerPolicy,
  ) : this(document, null, rootStore.currentRootUpdater, markerId, initialSpec, initialRange, policy)

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

  override fun getStartOffset(): Int = PMarkerResolution.startOffset(currentRange())

  override fun getEndOffset(): Int = PMarkerResolution.endOffset(currentRange())

  override fun getTextRange(): TextRange = PMarkerResolution.toTextRange(currentRange())

  override fun isValid(): Boolean {
    return !isDisposed() && isValidInRoot(currentRootReference().get()) &&
           (documentOrFile is Document || (documentOrFile as VirtualFile).isValid)
  }

  protected open fun isValidInRoot(root: PMarkerRoot): Boolean = root.isValid(markerId)

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

      if (!updateCurrentRoot { root -> if (isDisposed()) root else root.updateSpec(markerId, newSpec) }) return
      spec = newSpec
    }
  }

  /** An even version identifies a complete cache entry. An odd version identifies an update in progress. */
  @Volatile
  private var cachedResolutionVersion: Long = 0
  @Volatile
  private var cachedResolutionIdentity: Any? = null
  @Volatile
  private var cachedResolution: Long = PMarkerResolution.UNRESOLVED

  private fun currentRange(): Long {
    val resolution = currentResolution()
    return if (PMarkerResolution.isAbsent(resolution)) fallbackRange else resolution
  }

  internal fun cacheResolution(root: PMarkerRoot, resolution: Long) {
    val version = cachedResolutionVersion
    if (version and 1L != 0L || !CACHED_RESOLUTION_VERSION_UPDATER.compareAndSet(this, version, version + 1)) return
    cachedResolutionIdentity = cacheIdentity(root)
    cachedResolution = resolution
    cachedResolutionVersion = version + 2
  }

  /**
   * Resolves this marker in the current root. Reuses cached offsets for the same identity or advances them across one direct edit.
   * Traverses the tree when neither shortcut applies.
   */
  private fun currentResolution(): Long {
    val root = currentRootReference().get()
    val cacheIdentity = cacheIdentity(root)
    val version = cachedResolutionVersion
    val cachedIdentity = cachedResolutionIdentity
    val cached = cachedResolution
    val cacheIsStable = version and 1L == 0L && version == cachedResolutionVersion
    if (cacheIsStable && cached != PMarkerResolution.UNRESOLVED && cachedIdentity === cacheIdentity) {
      return cached
    }
    val advanced = if (cacheIsStable && cachedIdentity != null && !isDisposed() && root is PMarkerRootImpl) {
      root.advanceResolution(cachedIdentity, cached)
    }
    else {
      PMarkerResolution.UNRESOLVED
    }
    val resolution = if (advanced != PMarkerResolution.UNRESOLVED) advanced else SnapshotMarkerEngineImpl.resolveRangeMarker(this, root)
    cacheResolution(root, resolution)
    return resolution
  }

  private fun cacheIdentity(root: PMarkerRoot): Any = (root as? PMarkerRootImpl)?.resolutionCacheIdentity ?: root

  internal fun updateCurrentRoot(update: (PMarkerRoot) -> PMarkerRoot): Boolean {
    return rootStorage.updateCurrentRoot(update)
  }

  fun currentRootReference(): AtomicReference<PMarkerRoot> = rootStorage.currentRootReference()

  override fun toString(): String = "SnapshotRangeMarker(id=$markerId" +
                                    (if (isDisposed()) ", disposed" else "") +
                                    ")"

  private companion object {
    private const val ACTIVE: Int = 0
    private const val DISPOSING: Int = 1
    private const val DISPOSED: Int = 2
    private val DISPOSAL_STATE_UPDATER: AtomicIntegerFieldUpdater<SnapshotRangeMarkerImpl> =
      AtomicIntegerFieldUpdater.newUpdater(SnapshotRangeMarkerImpl::class.java, "disposalState")
    private val CACHED_RESOLUTION_VERSION_UPDATER: AtomicLongFieldUpdater<SnapshotRangeMarkerImpl> =
      AtomicLongFieldUpdater.newUpdater(SnapshotRangeMarkerImpl::class.java, "cachedResolutionVersion")
  }
}
