// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.elf.Elf
import com.intellij.openapi.editor.ex.DocumentCore
import com.intellij.openapi.editor.ex.DocumentEventDispatcher
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.editor.ex.DocumentMagicCore
import com.intellij.openapi.editor.ex.DocumentMutator
import com.intellij.openapi.editor.ex.RangeMarkerStorage
import com.intellij.openapi.editor.ex.DocumentSettings
import com.intellij.openapi.editor.ex.DocumentSnapshot
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater
import kotlin.concurrent.Volatile

/**
 * Runtime implementation of [DocumentMagicCore].
 *
 * A magic core owns one range-marker tree and two document views: elf and real.
 * The host document backed by this core is context-dependent:
 * - inside an elf scope, reads, events, and text mutations use the elf view;
 * - while delayed elf events are delivered on EDT after the scope, reads still use
 *   the elf view so listener code sees the same text as the event;
 * - outside those EDT-local contexts, including background threads, reads use the
 *   real view.
 *
 * The view choice must remain thread-local. Otherwise the temporary elf snapshot
 * visible during typing would leak to background readers that should observe only
 * the authoritative real document.
 *
 * Style convention: inner classes should be as minimal as possible, all logic
 * should be moved out to a separate file, and `this@DocumentMagicCoreImpl` should
 * be used explicitly.
 *
 * @see DocumentMagicCore
 */
internal class DocumentMagicCoreImpl private constructor(
  @Volatile private var snapshot: SnapshotSnapshot, // mutable via SNAPSHOT_UPDATER
  private val settingsElf: DocumentSettings,
  private val settingsReal: DocumentSettings,
): DocumentMagicCore {
  private val dispatcher: DocumentMagicEventDispatcher = DocumentMagicEventDispatcherImpl()
  private val rangeMarkers: RangeMarkerStorageImpl = RangeMarkerStorageImpl(dispatcher)
  private val guardedBlocks: GuardedBlocks = GuardedBlocksImpl(rangeMarkers)
  private val mutatorElf: DocumentElfMutator = DocumentElfMutatorImpl()
  private val mutatorReal: DocumentRealMutator = DocumentRealMutatorImpl()
  private val sync: ElfRealSync = ElfRealSyncImpl()
  private val liveElf: LiveElf = LiveElf()
  private val liveReal: LiveReal = LiveReal()
  private val viewElf: DocumentCore = DocumentElfCore()
  private val viewReal: DocumentCore = DocumentRealCore()
  @Volatile private var frozenElf: FrozenDocument? = null
  @Volatile private var frozenReal: FrozenDocument? = null

  override fun snapshot(): DocumentSnapshot {
    val snapshot = this.snapshot
    if (!snapshot.isDirty) {
      // it is a performance optimization for frequent path in hot method,
      // in most cases snapshot is clean, no need to call "expensive" isInElfScope
      return snapshot.real
    }
    return if (Elf.getElf().isInElfScope()) {
      snapshot.elf
    } else {
      snapshot.real
    }
  }

  override fun live(): CharSequence {
    return if (Elf.getElf().isInElfScope()) {
      liveElf
    } else {
      liveReal
    }
  }

  override fun rangeMarkers(): RangeMarkerStorage {
    return rangeMarkers
  }

  override fun guardedBlocks(): GuardedBlocks {
    return guardedBlocks
  }

  override fun dispatcher(): DocumentEventDispatcher {
    return if (Elf.getElf().isInElfScope()) {
      dispatcher.elf()
    } else {
      dispatcher.real()
    }
  }

  override fun mutator(): DocumentMutator {
    return if (Elf.getElf().isInElfScope()) {
      mutatorElf
    } else {
      mutatorReal
    }
  }

  override fun settings(): DocumentSettings {
    return settingsReal
  }

  override fun frozen(): FrozenDocument {
    return if (Elf.getElf().isInElfScope()) {
      getFrozenElf()
    } else {
      getFrozenReal()
    }
  }

  override fun elfCore(): DocumentCore {
    return viewElf
  }

  override fun realCore(): DocumentCore {
    return viewReal
  }

  private fun getFrozenElf(): FrozenDocument {
    return getFrozen(isElf=true)
  }

  private fun getFrozenReal(): FrozenDocument {
    return getFrozen(isElf=false)
  }

  private fun getFrozen(isElf: Boolean): FrozenDocument {
    var snapshot: DocumentSnapshot
    var frozen: FrozenDocument?
    if (isElf) {
      snapshot = this.snapshot.elf
      frozen = this.frozenElf
    } else {
      snapshot = this.snapshot.real
      frozen = this.frozenReal
    }
    if (frozen?.snapshot === snapshot) {
      return frozen
    }
    synchronized(this) {
      if (isElf) {
        snapshot = this.snapshot.elf
        frozen = this.frozenElf
      } else {
        snapshot = this.snapshot.real
        frozen = this.frozenReal
      }
      if (frozen?.snapshot === snapshot) {
        return frozen
      }
      frozen = FrozenDocument(snapshot)
      if (isElf) {
        this.frozenElf = frozen
      } else {
        this.frozenReal = frozen
      }
      return frozen
    }
  }

  private inner class ElfRealSyncImpl : ElfRealSync(mutatorElf, mutatorReal) {
    override fun getSnapshotSnapshot(): SnapshotSnapshot {
      return this@DocumentMagicCoreImpl.snapshot
    }

    override fun compareAndSet(expect: SnapshotSnapshot, update: SnapshotSnapshot): Boolean {
      return SNAPSHOT_UPDATER.compareAndSet(this@DocumentMagicCoreImpl, expect, update)
    }
  }

  private inner class DocumentElfCore : DocumentCore {
    override fun snapshot(): DocumentSnapshot {
      // TODO: hot method, optimize with cachedElf field
      return this@DocumentMagicCoreImpl.snapshot.elf
    }

    override fun live(): CharSequence {
      return this@DocumentMagicCoreImpl.liveElf
    }

    override fun rangeMarkers(): RangeMarkerStorage {
      return this@DocumentMagicCoreImpl.rangeMarkers
    }

    override fun guardedBlocks(): GuardedBlocks {
      return this@DocumentMagicCoreImpl.guardedBlocks
    }

    override fun dispatcher(): DocumentEventDispatcher {
      return this@DocumentMagicCoreImpl.dispatcher.elf()
    }

    override fun mutator(): DocumentMutator {
      return this@DocumentMagicCoreImpl.mutatorElf
    }

    override fun settings(): DocumentSettings {
      return this@DocumentMagicCoreImpl.settingsElf
    }

    override fun frozen(): DocumentEx {
      return this@DocumentMagicCoreImpl.getFrozenElf()
    }
  }

  private inner class DocumentRealCore : DocumentCore {
    override fun snapshot(): DocumentSnapshot {
      return this@DocumentMagicCoreImpl.snapshot.real
    }

    override fun live(): CharSequence {
      return this@DocumentMagicCoreImpl.liveReal
    }

    override fun rangeMarkers(): RangeMarkerStorage {
      return this@DocumentMagicCoreImpl.rangeMarkers
    }

    override fun guardedBlocks(): GuardedBlocks {
      return this@DocumentMagicCoreImpl.guardedBlocks
    }

    override fun dispatcher(): DocumentEventDispatcher {
      return this@DocumentMagicCoreImpl.dispatcher.real()
    }

    override fun mutator(): DocumentMutator {
      return this@DocumentMagicCoreImpl.mutatorReal
    }

    override fun settings(): DocumentSettings {
      return this@DocumentMagicCoreImpl.settingsReal
    }

    override fun frozen(): DocumentEx {
      return this@DocumentMagicCoreImpl.getFrozenReal()
    }
  }

  private inner class LiveElf : CharSequence {
    override val length: Int
      get() = this@DocumentMagicCoreImpl.snapshot.elf.textLength()

    override fun get(index: Int): Char {
      return this@DocumentMagicCoreImpl.snapshot.elf.text()[index]
    }

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
      return this@DocumentMagicCoreImpl.snapshot.elf.text().subSequence(startIndex, endIndex)
    }

    override fun toString(): String {
      return this@DocumentMagicCoreImpl.snapshot.elf.string()
    }
  }

  private inner class LiveReal : CharSequence {
    override val length: Int
      get() = this@DocumentMagicCoreImpl.snapshot.real.textLength()

    override fun get(index: Int): Char {
      return this@DocumentMagicCoreImpl.snapshot.real.text()[index]
    }

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
      return this@DocumentMagicCoreImpl.snapshot.real.text().subSequence(startIndex, endIndex)
    }

    override fun toString(): String {
      return this@DocumentMagicCoreImpl.snapshot.real.string()
    }
  }

  private inner class DocumentRealMutatorImpl : DocumentRealMutator(settingsReal, dispatcher, guardedBlocks) {
    override fun getSnapshot(): DocumentSnapshot {
      return this@DocumentMagicCoreImpl.snapshot.real
    }

    override fun getSnapshotSnapshot(): SnapshotSnapshot {
      return this@DocumentMagicCoreImpl.snapshot
    }

    override fun compareAndSet(expect: SnapshotSnapshot, update: SnapshotSnapshot): Boolean {
      return SNAPSHOT_UPDATER.compareAndSet(this@DocumentMagicCoreImpl, expect, update)
    }

    override fun appendRealChange(change: RealTextChange) {
      this@DocumentMagicCoreImpl.sync.appendRealLog(change)
    }

    override fun scheduleRealSync() {
      this@DocumentMagicCoreImpl.sync.scheduleRealSync()
    }
  }

  private inner class DocumentElfMutatorImpl : DocumentElfMutator(settingsElf, dispatcher, guardedBlocks) {
    override fun getSnapshot(): DocumentSnapshot {
      return this@DocumentMagicCoreImpl.snapshot.elf
    }

    override fun getSnapshotSnapshot(): SnapshotSnapshot {
      return this@DocumentMagicCoreImpl.snapshot
    }

    override fun compareAndSet(expect: SnapshotSnapshot, update: SnapshotSnapshot): Boolean {
      return SNAPSHOT_UPDATER.compareAndSet(this@DocumentMagicCoreImpl, expect, update)
    }

    override fun appendElfChange(change: ElfTextChange) {
      this@DocumentMagicCoreImpl.sync.appendElfLog(change)
    }
  }

  private inner class DocumentMagicEventDispatcherImpl : DocumentMagicEventDispatcher(settingsElf, settingsReal) {
    override fun getSnapshotSnapshot(): SnapshotSnapshot {
      return this@DocumentMagicCoreImpl.snapshot
    }
  }

  companion object {
    @JvmStatic
    fun createCore(chars: CharSequence, acceptSlashR: Boolean, forUseInNonAWTThread: Boolean): DocumentCore {
      val settingsReal = DocumentSettingsImpl(!forUseInNonAWTThread, acceptSlashR, chars)
      val settingsElf = DocumentElfSettingsImpl(settingsReal)
      val snapshot = SnapshotSnapshot.newClean(DocumentSnapshotImpl(chars))
      return DocumentMagicCoreImpl(snapshot, settingsElf, settingsReal)
    }

    /**
     * [snapshot] is a performance-critical field, it cannot be replaced with AtomicReference
     */
    private val SNAPSHOT_UPDATER: AtomicReferenceFieldUpdater<DocumentMagicCoreImpl, SnapshotSnapshot> =
      AtomicReferenceFieldUpdater.newUpdater(
        DocumentMagicCoreImpl::class.java,
        SnapshotSnapshot::class.java,
        "snapshot",
      )
  }
}
