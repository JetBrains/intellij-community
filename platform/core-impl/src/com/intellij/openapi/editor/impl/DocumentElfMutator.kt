// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.elf.Elf
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.ex.DocumentRangeMarkerTree
import com.intellij.openapi.editor.ex.DocumentSettings
import com.intellij.openapi.editor.ex.DocumentSnapshot
import com.intellij.openapi.editor.impl.event.DocumentEventImpl
import com.intellij.util.DocumentEventUtil
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.text.ImmutableCharSequence
import java.util.function.UnaryOperator
import kotlin.concurrent.Volatile

/**
 * Mutator for the elf document view.
 *
 * Direct text writes are allowed only inside an elf scope. They update only the
 * elf snapshot, fire elf listener callbacks, and append an [ElfTextChange] for a
 * later sync pass. Those writes do not take the application write lock.
 *
 * Reverting elf changes and applying real changes to elf are synchronization
 * operations. They run on EDT with write access, outside an elf scope, and keep
 * the UI-side view consistent with the real document after conflict handling or
 * real-document edits.
 */
internal abstract class DocumentElfMutator(
  private val settingsElf: DocumentSettings,
  private val dispatcher: DocumentMagicEventDispatcher,
  tree: DocumentRangeMarkerTree,
) : DocumentMutatorImpl(settingsElf, dispatcher, tree) {
  @Volatile private var textChangeInProgress = false
  @Volatile private var isApplyingRealChangesToElf = false
  @Volatile private var revertingChangeEvent: DocumentEvent? = null

  protected abstract fun getSnapshotSnapshot(): SnapshotSnapshot
  protected abstract fun compareAndSet(expect: SnapshotSnapshot, update: SnapshotSnapshot): Boolean
  protected abstract fun appendElfChange(change: ElfTextChange)

  fun revertElfChanges(changes: List<ElfTextChange>) {
    ThreadingAssertions.assertEventDispatchThread()
    ThreadingAssertions.assertWriteAccess()
    assertOutsideElfScope()
    check(changes.isNotEmpty()) { "no elf changes to revert" }
    val initialBulkModeStatus = dispatcher.elf().isInBulkUpdate()
    val hostDocument = changes.first().changeEvent.document
    try {
      for (change in changes.asReversed()) {
        dispatcher.setBulkElfUpdateStatus(hostDocument, change.isInBulkUpdate)
        revertChange(change)
      }
    } finally {
      dispatcher.setBulkElfUpdateStatus(hostDocument, initialBulkModeStatus)
    }
  }

  fun applyRealChanges(changes: List<RealTextChange>) {
    ThreadingAssertions.assertEventDispatchThread()
    ThreadingAssertions.assertWriteAccess()
    assertOutsideElfScope()
    check(changes.isNotEmpty()) { "no real changes to apply" }
    val initialBulkModeStatus = dispatcher.elf().isInBulkUpdate()
    val hostDocument = changes.first().changeEvent.document
    isApplyingRealChangesToElf = true
    try {
      for (change in changes) {
        dispatcher.setBulkElfUpdateStatus(hostDocument, change.isInBulkUpdate)
        changeText(
          getSnapshot(),
          change.changeEvent,
          change.snapshotAfter.text(),
          DocumentModStamp.next(),
          false, // TODO: why false?
        )
      }
    } finally {
      isApplyingRealChangesToElf = false
      dispatcher.setBulkElfUpdateStatus(hostDocument, initialBulkModeStatus)
    }
  }

  final override fun setModStamp(newModStamp: Long, incrementModSequence: Boolean) {
    assertIsInElfScope()
    throw UnsupportedOperationException("ElfDocument does not support setModStamp yet")
  }

  final override fun clearLineFlags(startLine: Int, endLine: Int, exceptLines: IntArray) {
    assertIsInElfScope()
    throw UnsupportedOperationException("ElfDocument does not support clearLineFlags yet")
  }

  final override fun insertString(
    hostDocument: Document,
    insertOffset: Int,
    insertString: CharSequence,
  ) {
    assertIsInElfScope()
    super.insertString(hostDocument, insertOffset, insertString)
  }

  final override fun deleteString(hostDocument: Document, startOffset: Int, endOffset: Int) {
    assertIsInElfScope()
    super.deleteString(hostDocument, startOffset, endOffset)
  }

  final override fun replaceText(
    hostDocument: Document,
    newWholeText: CharSequence,
    newModStamp: Long,
  ) {
    assertIsInElfScope()
    super.replaceText(hostDocument, newWholeText, newModStamp)
  }

  final override fun setText(hostDocument: Document, newWholeText: CharSequence) {
    assertIsInElfScope()
    super.setText(hostDocument, newWholeText)
  }

  final override fun moveText(
    hostDocument: Document,
    srcStartOffset: Int,
    srcEndOffset: Int,
    dstOffset: Int,
  ) {
    assertIsInElfScope()
    super.moveText(hostDocument, srcStartOffset, srcEndOffset, dstOffset)
  }

  final override fun replaceString(
    hostDocument: Document,
    startOffset: Int,
    endOffset: Int,
    moveOffset: Int,
    replaceString: CharSequence,
    newModStamp: Long,
    wholeTextReplaced: Boolean,
  ) {
    assertIsInElfScope()
    super.replaceString(
      hostDocument,
      startOffset,
      endOffset,
      moveOffset,
      replaceString,
      newModStamp,
      wholeTextReplaced,
    )
  }

  final override fun updateAndGet(update: UnaryOperator<DocumentSnapshot>): DocumentSnapshot {
    while (true) {
      val expect = getSnapshotSnapshot()
      val newElf = update.apply(expect.elf)
      val updated = SnapshotSnapshot.newDirty(newElf, expect.real) // any elf change makes snapshot dirty
      if (compareAndSet(expect, updated)) {
        // if metadata change is supported, then should schedule elfToRealChange
        return newElf
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
    val snapshotAfter: DocumentSnapshot
    textChangeInProgress = true
    try {
      snapshotAfter = dispatcher.withFiringElfTextUpdate(revertingChangeEvent, changeEvent) {
        updateText(snapshotBefore, changeEvent, newWholeText, newModStamp, clearLineFlags)
      }
    } finally {
      textChangeInProgress = false
    }
    if (revertingChangeEvent == null && !isApplyingRealChangesToElf) {
      settingsElf.assertInsideCommand() // currently no difference, but real settings would be more accurate
      appendElfChange(
        ElfTextChange(
          snapshotBefore,
          changeEvent,
          newWholeText,
          newModStamp,
          clearLineFlags,
          dispatcher.elf().isInBulkUpdate(),
          CommandProcessor.getInstance().currentCommandProject,
          CommandProcessor.getInstance().currentCommandName,
          CommandProcessor.getInstance().currentCommandGroupId,
          CommandProcessor.getInstance().isUndoTransparentActionInProgress,
        )
      )
    }
    return snapshotAfter
  }

  private fun updateText(
    snapshotBefore: DocumentSnapshot,
    changeEvent: DocumentEvent,
    newWholeText: ImmutableCharSequence,
    newModStamp: Long,
    clearLineFlags: Boolean,
  ): DocumentSnapshot {
    return updateAndGet { latest ->
      // modStamp or other metadata could be changed during before-change listeners, should merge it into final snapshot
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

  private fun revertChange(change: ElfTextChange) {
    val eventToRevert = change.changeEvent
    val currentSnapshot = getSnapshot() // safe to get snapshot because outside elfScope
    val initialStartOffset = if (eventToRevert is DocumentEventImpl) eventToRevert.initialStartOffset else eventToRevert.offset
    val changeEvent = DocumentEventImpl(
      eventToRevert.document,
      eventToRevert.offset,
      eventToRevert.newFragment,
      eventToRevert.oldFragment,
      currentSnapshot.modStamp(),
      eventToRevert.isWholeTextReplaced,
      initialStartOffset,
      eventToRevert.newLength,
      getRevertMoveOffset(eventToRevert),
      currentSnapshot.textLength(),
    )
    revertingChangeEvent = eventToRevert
    try {
      changeText(
        currentSnapshot,
        changeEvent,
        change.snapshotBefore.text(),
        DocumentModStamp.next(),
        change.clearLineFlags,
      )
    } finally {
      revertingChangeEvent = null
    }
  }

  private fun getRevertMoveOffset(changeEvent: DocumentEvent): Int {
    return if (DocumentEventUtil.isMoveDeletion(changeEvent)) {
      DocumentEventUtil.getMoveOffsetAfterDeletion(changeEvent)
    } else {
      changeEvent.moveOffset
    }
  }

  private fun assertNotNestedModification() {
    if (textChangeInProgress) {
      throw IllegalStateException("Detected document modification from DocumentListener")
    }
  }

  private fun assertIsInElfScope() {
    if (!Elf.getElf().isInElfScope()) {
      throw IllegalStateException("ElfDocument is mutable only within elf scope")
    }
  }

  private fun assertOutsideElfScope() {
    if (Elf.getElf().isInElfScope()) {
      throw IllegalStateException("operation is forbidden inside elfScope")
    }
  }
}
