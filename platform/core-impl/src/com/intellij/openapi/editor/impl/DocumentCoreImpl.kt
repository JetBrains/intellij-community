// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.ex.DocumentCore
import com.intellij.openapi.editor.ex.DocumentEventDispatcher
import com.intellij.openapi.editor.ex.DocumentMutator
import com.intellij.openapi.editor.ex.DocumentPatch
import com.intellij.openapi.editor.ex.DocumentSettings
import com.intellij.openapi.editor.ex.experimental.Agent
import com.intellij.openapi.editor.ex.experimental.DocumentBranch
import com.intellij.openapi.editor.impl.marker.SnapshotMarkerStores
import kotlin.concurrent.Volatile

/**
 * Default implementation of [DocumentImpl]
 */
internal class DocumentCoreImpl private constructor(
  @Volatile private var snapshot: DocumentBranch,
  private val settings: DocumentSettings,
  private val dispatcher: DocumentEventDispatcherImpl,
) : DocumentCore {
  private val live: CharSequence = LiveCharSequence()
  private val snapshotMarkerStores: SnapshotMarkerStores = SnapshotMarkerStores()
  private val mutator: DocumentMutator = MutatorImpl()
  @Volatile private var frozen: FrozenDocument? = null

  override fun snapshot(): DocumentBranch {
    return snapshot
  }

  override fun live(): CharSequence {
    return live
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
    if (frozen != null && isFrozenFrom(frozen, snapshot)) {
      return frozen
    }
    synchronized(this) {
      val snapshot = this.snapshot
      var frozen = this.frozen
      if (frozen != null && isFrozenFrom(frozen, snapshot)) {
        return frozen
      }
      frozen = FrozenDocument(snapshot.text(), snapshot.modState().stamp())
      this.frozen = frozen
      return frozen
    }
  }

  /**
   * Whether [frozen] has the text and the modification stamp of [snapshot]. A frozen document holds nothing else.
   */
  private fun isFrozenFrom(frozen: FrozenDocument, snapshot: DocumentBranch): Boolean {
    return frozen.documentText === snapshot.text() && frozen.modificationStamp == snapshot.modState().stamp()
  }

  private inner class LiveCharSequence : CharSequence {
    override val length: Int
      get() = this@DocumentCoreImpl.snapshot.text().length()

    override fun get(index: Int): Char {
      return this@DocumentCoreImpl.snapshot.text().chars()[index]
    }

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
      return this@DocumentCoreImpl.snapshot.text().chars().subSequence(startIndex, endIndex)
    }

    override fun toString(): String {
      return this@DocumentCoreImpl.snapshot.text().string()
    }
  }

  private inner class MutatorImpl : DocumentMutatorImpl(settings, dispatcher, snapshotMarkerStores) {
    override fun getSnapshot(): DocumentBranch {
      return this@DocumentCoreImpl.snapshot
    }

    override fun updateAndGet(
      initialSnapshot: DocumentBranch?,
      patch: DocumentPatch?,
      update: (DocumentBranch) -> DocumentBranch,
    ): DocumentBranch {
      while (true) {
        val expected = this@DocumentCoreImpl.snapshot
        val before = if (initialSnapshot == null || expected.text() === initialSnapshot.text()) expected else initialSnapshot
        val after = trimmed(update(before))
        if (snapshotMarkerStores.tryCommit(
            before,
            after,
            patch,
            isCurrent = { this@DocumentCoreImpl.snapshot === expected },
            publish = { this@DocumentCoreImpl.snapshot = after },
          )) {
          return after
        }
      }
    }
  }

  companion object {
    /**
     * The agent that every document core edits under. The lineage of each branch keeps two documents apart.
     */
    private val MAIN_AGENT: Agent = Agent.createAgent("main")

    @JvmStatic
    fun createCore(chars: CharSequence, acceptSlashR: Boolean, forUseInNonAWTThread: Boolean): DocumentCore {
      val settings = DocumentSettingsImpl(!forUseInNonAWTThread, acceptSlashR, chars)
      val dispatcher = DocumentEventDispatcherImpl(settings)
      val branch = DocumentBranch.createBranch(chars, MAIN_AGENT)
      return DocumentCoreImpl(branch, settings, dispatcher)
    }

    /**
     * [branch] trimmed to its version. The core keeps no history, so it publishes only branches with no events.
     */
    private fun trimmed(branch: DocumentBranch): DocumentBranch {
      return branch.trimmedTo(branch.version())
    }
  }
}
