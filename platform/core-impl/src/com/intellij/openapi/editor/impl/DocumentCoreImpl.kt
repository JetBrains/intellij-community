// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.ex.DocumentCore
import com.intellij.openapi.editor.ex.DocumentEventDispatcher
import com.intellij.openapi.editor.ex.DocumentMutator
import com.intellij.openapi.editor.ex.DocumentRangeMarkerTree
import com.intellij.openapi.editor.ex.DocumentSettings
import com.intellij.openapi.editor.ex.DocumentSnapshot
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater
import java.util.function.UnaryOperator
import kotlin.concurrent.Volatile

/**
 * Default implementation of [DocumentImpl]
 */
internal class DocumentCoreImpl private constructor(
  @Volatile private var snapshot: DocumentSnapshot, // mutable via SNAPSHOT_UPDATER
  private val settings: DocumentSettings,
  private val dispatcher: DocumentEventDispatcherImpl,
  private val tree: DocumentRangeMarkerTree,
) : DocumentCore {
  private val live: CharSequence = LiveCharSequence()
  private val mutator: DocumentMutator = MutatorImpl()
  @Volatile private var frozen: FrozenDocument? = null

  override fun snapshot(): DocumentSnapshot {
    return snapshot
  }

  override fun live(): CharSequence {
    return live
  }

  override fun tree(): DocumentRangeMarkerTree {
    return tree
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
      get() = this@DocumentCoreImpl.snapshot.textLength()

    override fun get(index: Int): Char {
      return this@DocumentCoreImpl.snapshot.text()[index]
    }

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
      return this@DocumentCoreImpl.snapshot.text().subSequence(startIndex, endIndex)
    }

    override fun toString(): String {
      return this@DocumentCoreImpl.snapshot.string()
    }
  }

  private inner class MutatorImpl : DocumentMutatorImpl(settings, dispatcher, tree) {
    override fun getSnapshot(): DocumentSnapshot {
      return this@DocumentCoreImpl.snapshot
    }

    override fun updateAndGet(update: UnaryOperator<DocumentSnapshot>): DocumentSnapshot {
      return SNAPSHOT_UPDATER.updateAndGet(this@DocumentCoreImpl, update)
    }
  }

  companion object {
    @JvmStatic
    fun createCore(chars: CharSequence, acceptSlashR: Boolean, forUseInNonAWTThread: Boolean): DocumentCore {
      val settings = DocumentSettingsImpl(!forUseInNonAWTThread, acceptSlashR, chars)
      val dispatcher = DocumentEventDispatcherImpl(settings)
      val tree = DocumentRangeMarkerTreeImpl(dispatcher)
      val snapshot = DocumentSnapshotImpl(chars)
      return DocumentCoreImpl(snapshot, settings, dispatcher, tree)
    }

    /**
     * [snapshot] is a performance-critical field, it cannot be replaced with AtomicReference
     */
    private val SNAPSHOT_UPDATER: AtomicReferenceFieldUpdater<DocumentCoreImpl, DocumentSnapshot> =
      AtomicReferenceFieldUpdater.newUpdater(
        DocumentCoreImpl::class.java,
        DocumentSnapshot::class.java,
        "snapshot",
      )
  }
}
