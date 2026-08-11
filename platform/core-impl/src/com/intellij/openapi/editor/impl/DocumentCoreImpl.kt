// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.ex.DocumentCore
import com.intellij.openapi.editor.ex.DocumentEventDispatcher
import com.intellij.openapi.editor.ex.DocumentMutator
import com.intellij.openapi.editor.ex.RangeMarkerStorage
import com.intellij.openapi.editor.ex.DocumentSettings
import com.intellij.openapi.editor.ex.DocumentText
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater
import java.util.function.UnaryOperator
import kotlin.concurrent.Volatile

/**
 * Default implementation of [DocumentImpl]
 */
internal class DocumentCoreImpl private constructor(
  @Volatile private var snapshot: DocumentText, // mutable via SNAPSHOT_UPDATER
  private val settings: DocumentSettings,
  private val dispatcher: DocumentEventDispatcherImpl,
  private val rangeMarkers: RangeMarkerStorage,
) : DocumentCore {
  private val guardedBlocks: GuardedBlocks = GuardedBlocksImpl(rangeMarkers as RangeMarkerStorageImpl)
  private val live: CharSequence = LiveCharSequence()
  private val mutator: DocumentMutator = MutatorImpl()
  @Volatile private var frozen: FrozenDocument? = null

  override fun snapshot(): DocumentText {
    return snapshot
  }

  override fun live(): CharSequence {
    return live
  }

  override fun rangeMarkers(): RangeMarkerStorage {
    return rangeMarkers
  }

  override fun guardedBlocks(): GuardedBlocks {
    return guardedBlocks
  }

  override fun dispatcher(): DocumentEventDispatcher {
    return dispatcher
  }

  override fun settings(): DocumentSettings {
    return settings
  }

  override fun mutator(): DocumentMutator {
    return mutator
  }

  override fun frozen(): FrozenDocument {
    val snapshot = this.snapshot
    val frozen = this.frozen
    if (frozen != null && frozen.snapshot === snapshot) {
      return frozen
    }
    synchronized(this) {
      val snapshot = this.snapshot
      var frozen = this.frozen
      if (frozen != null && frozen.snapshot === snapshot) {
        return frozen
      }
      frozen = FrozenDocument(snapshot)
      this.frozen = frozen
      return frozen
    }
  }

  private inner class LiveCharSequence : CharSequence {
    override val length: Int
      get() = this@DocumentCoreImpl.snapshot.length()

    override fun get(index: Int): Char {
      return this@DocumentCoreImpl.snapshot.chars()[index]
    }

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
      return this@DocumentCoreImpl.snapshot.chars().subSequence(startIndex, endIndex)
    }

    override fun toString(): String {
      return this@DocumentCoreImpl.snapshot.string()
    }
  }

  private inner class MutatorImpl : DocumentMutatorImpl(settings, dispatcher, guardedBlocks) {
    override fun getSnapshot(): DocumentText {
      return this@DocumentCoreImpl.snapshot
    }

    override fun updateAndGet(update: UnaryOperator<DocumentText>): DocumentText {
      return SNAPSHOT_UPDATER.updateAndGet(this@DocumentCoreImpl, update)
    }
  }

  companion object {
    @JvmStatic
    fun createCore(chars: CharSequence, acceptSlashR: Boolean, forUseInNonAWTThread: Boolean): DocumentCore {
      val settings = DocumentSettingsImpl(!forUseInNonAWTThread, acceptSlashR, chars)
      val dispatcher = DocumentEventDispatcherImpl(settings)
      val tree = RangeMarkerStorageImpl(dispatcher)
      val snapshot = DocumentTextImpl(chars)
      return DocumentCoreImpl(snapshot, settings, dispatcher, tree)
    }

    /**
     * [snapshot] is a performance-critical field, it cannot be replaced with AtomicReference
     */
    private val SNAPSHOT_UPDATER: AtomicReferenceFieldUpdater<DocumentCoreImpl, DocumentText> =
      AtomicReferenceFieldUpdater.newUpdater(
        DocumentCoreImpl::class.java,
        DocumentText::class.java,
        "snapshot",
      )
  }
}
