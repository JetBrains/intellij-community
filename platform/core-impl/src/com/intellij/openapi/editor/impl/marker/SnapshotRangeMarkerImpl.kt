// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ex.DocumentSnapshot
import com.intellij.openapi.editor.ex.RangeMarkerEx
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.impl.DocumentSnapshotImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicReference

/**
 * Ordinary range marker whose offsets are stored in the snapshot marker engine.
 */
@ApiStatus.Internal
open class SnapshotRangeMarkerImpl internal constructor(
  document: Document,
  internal val fileRoot: FileMarkerRoot?,
  internal val markerId: Long,
  initialSpec: MarkerSpec,
  internal val initialRange: TextRange,
) : UserDataHolderBase(), PMarker, RangeMarkerEx {
  protected constructor(
    document: Document,
    markerId: Long,
    initialSpec: MarkerSpec,
    initialRange: TextRange,
  ) : this(document, null, markerId, initialSpec, initialRange)

  private val documentOrFile: Any = fileRoot?.file ?: document

  @Volatile
  internal var disposed: Boolean = false
    private set

  internal fun markDisposed() {
    disposed = true
  }

  @Volatile
  private var spec = initialSpec

  override fun getId(): Long {
    return markerId
  }

  override fun resolve(snapshot: DocumentSnapshot): PMarkerResolution {
    return SnapshotMarkerEngineImpl.resolveRangeMarker(this, rootReference(snapshot).get())
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

  override fun isValid(): Boolean = !disposed && currentResolution().isValid && (documentOrFile is Document || (documentOrFile as VirtualFile).isValid)

  override fun isGreedyToLeft(): Boolean = spec.isGreedyToLeft

  override fun isGreedyToRight(): Boolean = spec.isGreedyToRight

  override fun setGreedyToLeft(greedy: Boolean) {
    updateSpec { it.copy(isGreedyToLeft = greedy) }
  }

  override fun setGreedyToRight(greedy: Boolean) {
    updateSpec { it.copy(isGreedyToRight = greedy) }
  }

  override fun setStickingToRight(value: Boolean) {
    updateSpec { it.copy(isStickingToRight = value) }
  }

  protected fun isStickingToRight(): Boolean = spec.isStickingToRight

  @Synchronized
  override fun dispose() {
    if (disposed) return
    beforeDispose()
    SnapshotMarkerEngineImpl.removeRangeMarker(this)
    afterDispose()
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

      val rootReference = currentRootReference()
      while (!disposed) {
        val oldRoot = rootReference.get()
        val newRoot = oldRoot.updateSpec(markerId, newSpec)
        if (newRoot === oldRoot) return
        if (rootReference.compareAndSet(oldRoot, newRoot)) {
          spec = newSpec
          return
        }
      }
    }
  }

  private data class CachedResolution(val root: PMarkerRoot, val resolution: PMarkerResolution)

  private var cachedResolution: CachedResolution? = null

  private fun currentResolution(): PMarkerResolution {
    val root = currentRootReference().get()
    val cached = cachedResolution
    if (cached != null && cached.root === root) {
      return cached.resolution
    }
    val resolution = SnapshotMarkerEngineImpl.resolveRangeMarker(this, root)
    this.cachedResolution = CachedResolution(root, resolution)
    return resolution
  }

  @ApiStatus.Internal
  open fun currentRootReference(): AtomicReference<PMarkerRoot> {
    val documentOrFile = documentOrFile
    if (documentOrFile is VirtualFile) {
      return checkNotNull(fileRoot).rootReference()
    }
    val document = documentOrFile as DocumentImpl
    return (document.core.snapshot() as DocumentSnapshotImpl).markerRoot
  }

  protected open fun rootReference(snapshot: DocumentSnapshot): AtomicReference<PMarkerRoot> {
    return (snapshot as DocumentSnapshotImpl).markerRoot
  }


  override fun toString(): String = "SnapshotRangeMarker(id=$markerId" +
                                    (if (disposed) ", disposed" else "") +
                                                   ")"
}
