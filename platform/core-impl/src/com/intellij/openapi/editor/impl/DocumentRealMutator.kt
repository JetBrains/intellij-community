// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.elf.Elf
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.ex.DocumentRangeMarkerTree
import com.intellij.openapi.editor.ex.DocumentSettings
import com.intellij.openapi.editor.ex.DocumentSnapshot
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.text.ImmutableCharSequence
import java.util.function.UnaryOperator
import kotlin.concurrent.Volatile

/**
 * Mutator for the authoritative real document view.
 *
 * When there is no elf barrier, a real text change updates the shared clean
 * snapshot and notifies both real and elf listeners. During an elf scope, or
 * while pending elf changes keep the snapshot dirty, the real change updates only
 * the real snapshot and is logged as a [RealTextChange]. A later sync pass applies
 * that change to the elf view before pending elf changes are rebased.
 *
 * Replaying [ElfTextChange] instances to the real document restores the command
 * and undo-transparent metadata captured when the elf edit was made.
 */
internal abstract class DocumentRealMutator(
  settings: DocumentSettings,
  private val dispatcher: DocumentMagicEventDispatcher,
  tree: DocumentRangeMarkerTree,
) : DocumentMutatorImpl(settings, dispatcher, tree) {
  @Volatile private var textChangeInProgress = false
  @Volatile private var isApplyingElfChangesToReal = false

  protected abstract fun getSnapshotSnapshot(): SnapshotSnapshot
  protected abstract fun compareAndSet(expect: SnapshotSnapshot, update: SnapshotSnapshot): Boolean
  protected abstract fun appendRealChange(change: RealTextChange)
  protected abstract fun scheduleRealSync()

  fun applyElfTextChanges(changes: List<ElfTextChange>) {
    ThreadingAssertions.assertEventDispatchThread()
    ThreadingAssertions.assertWriteAccess()
    assertOutsideElfScope()
    check(changes.isNotEmpty()) { "no elf changes to apply" }
    val initialBulkModeStatus = dispatcher.real().isInBulkUpdate()
    val hostDocument: Document = changes.first().changeEvent.document
    isApplyingElfChangesToReal = true
    try {
      for (change in changes) {
        dispatcher.setBulkModeStatus(hostDocument, change.isInBulkUpdate)
        applyElfTextChange(change)
      }
    } finally {
      isApplyingElfChangesToReal = false
      dispatcher.setBulkModeStatus(hostDocument, initialBulkModeStatus)
    }
  }

  final override fun updateAndGet(update: UnaryOperator<DocumentSnapshot>): DocumentSnapshot {
    while (true) {
      val expect = getSnapshotSnapshot()
      val newReal = update.apply(expect.real)
      val updated = if (elfBarrier(expect)) {
        SnapshotSnapshot.newDirty(expect.elf, newReal)
      } else {
        SnapshotSnapshot.newClean(newReal)
      }
      if (compareAndSet(expect, updated)) {
        if (!expect.isDirty && updated.isDirty) {
          // snapshot was clean, but real change occurred within elfScope
          scheduleRealSync()
        }
        return newReal
      }
    }
  }

  final override fun changeText(
    snapshotBefore: DocumentSnapshot,
    changeEvent: DocumentEvent,
    newWholeText: ImmutableCharSequence,
    newModStamp: Long,
    clearLineFlags: Boolean,
  ): DocumentSnapshot {
    assertNotNestedModification()
    textChangeInProgress = true
    try {
      return changeAndFireText(
        snapshotBefore,
        changeEvent,
        newWholeText,
        newModStamp,
        clearLineFlags,
      )
    } finally {
      textChangeInProgress = false
    }
  }

  private fun changeAndFireText(
    snapshotBefore: DocumentSnapshot,
    changeEvent: DocumentEvent,
    newWholeText: ImmutableCharSequence,
    newModStamp: Long,
    clearLineFlags: Boolean,
  ): DocumentSnapshot {
    if (elfBarrier()) {
      val snapshotAfterChange = dispatcher.withFiringTextUpdate(changeEvent) {
        updateText(snapshotBefore, changeEvent, newWholeText, newModStamp, clearLineFlags)
      }
      if (!isApplyingElfChangesToReal) {
        appendRealChange(RealTextChange(
          changeEvent,
          snapshotAfterChange,
          dispatcher.real().isInBulkUpdate(),
        ))
      }
      return snapshotAfterChange
    } else {
      return dispatcher.withFiringBothTextUpdate(changeEvent) {
        updateText(snapshotBefore, changeEvent, newWholeText, newModStamp, clearLineFlags)
      }
    }
  }

  private fun updateText(
    snapshotBefore: DocumentSnapshot,
    changeEvent: DocumentEvent,
    newWholeText: ImmutableCharSequence,
    newModStamp: Long,
    clearLineFlags: Boolean,
  ): DocumentSnapshot {
    return updateAndGet { latest ->
      // modStamp or other metadata could be changed during before-change listeners,
      // should merge it into final snapshot
      val merged = snapshotBefore.withMetadata(latest)
      merged.withText(
        newWholeText,
        changeEvent.offset,
        changeEvent.offset + changeEvent.oldLength,
        changeEvent.newFragment,
        newModStamp,
        changeEvent.isWholeTextReplaced,
        clearLineFlags,
        false,
      )
    }
  }

  private fun applyElfTextChange(elfChange: ElfTextChange) {
    if (elfChange.isTransparent) {
      CommandProcessor.getInstance().runUndoTransparentAction {
        changeText(
          elfChange.snapshotBefore,
          elfChange.changeEvent,
          elfChange.newWholeText,
          elfChange.newModStamp,
          elfChange.clearLineFlags,
        )
      }
      return
    }
    Elf.getElf().executeElfCommand(
      elfChange.project,
      elfChange.commandName,
      elfChange.commandGroupId,
    ) {
      changeText(
        elfChange.snapshotBefore,
        elfChange.changeEvent,
        elfChange.newWholeText,
        elfChange.newModStamp,
        elfChange.clearLineFlags,
      )
    }
  }

  private fun elfBarrier(): Boolean {
    ThreadingAssertions.assertEventDispatchThread()
    // reading snapshot is safe because elfScope or isDirty can be changed only on EDT
    val snapshot = getSnapshotSnapshot()
    return elfBarrier(snapshot)
  }

  /**
   * Contract: elf should not observe real changes until elf changes are applied to real document
   */
  private fun elfBarrier(snapshot: SnapshotSnapshot): Boolean {
    return snapshot.isDirty || Elf.getElf().isInElfScope()
  }

  private fun assertNotNestedModification() {
    if (textChangeInProgress) {
      throw IllegalStateException("Detected document modification from DocumentListener")
    }
  }

  private fun assertOutsideElfScope() {
    if (Elf.getElf().isInElfScope()) {
      throw IllegalStateException("operation is forbidden inside elfScope")
    }
  }
}
