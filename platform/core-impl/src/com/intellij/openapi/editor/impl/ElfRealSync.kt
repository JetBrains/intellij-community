// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.elf.Elf
import com.intellij.openapi.editor.ex.DocumentSnapshot
import com.intellij.openapi.editor.impl.event.DocumentEventImpl
import com.intellij.util.DocumentEventUtil
import com.intellij.util.concurrency.ThreadingAssertions
import java.util.concurrent.LinkedBlockingQueue

/**
 * Synchronizes the elf view with the authoritative real document.
 *
 * Text changes are recorded into two queues. Pending elf changes are edits made
 * on the typing view. Real changes are edits made to the real document while the
 * elf and real snapshots could not be merged immediately.
 *
 * A sync pass always runs on EDT, with write access, and outside an elf scope.
 * It handles three cases:
 * - only elf changes: replay them to the real document;
 * - only real changes: apply them to the elf view and mark both views clean;
 * - both kinds of changes: revert pending elf changes, apply real changes to elf,
 *   then rebase and replay the non-conflicting elf prefix to the real document.
 *
 * The rebase policy is intentionally minimal. The first conflicting elf change,
 * and all following elf changes from the same batch, stay reverted and are not
 * replayed to the real document.
 */
internal abstract class ElfRealSync(
  private val mutatorElf: DocumentElfMutator,
  private val mutatorReal: DocumentRealMutator,
) {
  private val scheduler = SchedulerImpl()
  private val pendingElfChanges = LinkedBlockingQueue<ElfTextChange>()
  private val realChanges = LinkedBlockingQueue<RealTextChange>()

  protected abstract fun getSnapshotSnapshot(): SnapshotSnapshot
  protected abstract fun compareAndSet(expect: SnapshotSnapshot, update: SnapshotSnapshot): Boolean

  fun appendElfLog(change: ElfTextChange) {
    pendingElfChanges.add(change)
    scheduler.schedule()
  }

  fun appendRealLog(change: RealTextChange) {
    realChanges.add(change)
    scheduler.schedule()
  }

  fun scheduleRealSync() {
    scheduler.schedule()
  }

  private fun applyElfChangesToReal() {
    ThreadingAssertions.assertEventDispatchThread()
    ThreadingAssertions.assertWriteAccess()
    assertOutsideElfScope()
    val elfChanges = pendingElfChanges.drainAll()
    val realChanges = realChanges.drainAll()
    if (elfChanges.isEmpty()) {
      // if a background metadata update wins after this read,
      // it creates a clean shared snapshot and needs no sync
      val isDirty = getSnapshotSnapshot().isDirty
      if (realChanges.isEmpty()) {
        if (isDirty) {
          markClean()
        }
        return
      }
      if (!isDirty) {
        throw IllegalStateException("Elf is clean but has real changes to apply")
      }
      mutatorElf.applyRealChanges(realChanges)
      markClean()
      return
    }
    if (realChanges.isEmpty()) {
      applyElfChangesToReal(elfChanges)
    } else {
      rebaseAndApplyElfChangesToReal(elfChanges, realChanges)
    }
    markClean()
  }

  private fun applyElfChangesToReal(changes: List<ElfTextChange>) {
    mutatorReal.applyElfTextChanges(changes)
  }

  private fun rebaseAndApplyElfChangesToReal(elfChanges: List<ElfTextChange>, realChanges: List<RealTextChange>) {
    mutatorElf.revertElfChanges(elfChanges)
    mutatorElf.applyRealChanges(realChanges)
    markClean() // make rebased elf changes apply to both snapshots, not through the dirty real-only barrier
    val rebased = rebaseElfPending(elfChanges, realChanges)
    if (rebased.isNotEmpty()) {
      mutatorReal.applyElfTextChanges(rebased)
    }
  }

  private fun rebaseElfPending(elfChanges: List<ElfTextChange>, realChanges: List<RealTextChange>): List<ElfTextChange> {
    val rebasedChanges = mutableListOf<ElfTextChange>()
    var real = realChanges.last().snapshotAfter
    for (change in elfChanges) {
      val rebasedChange = rebaseElfTextChange(change, real, realChanges)
      if (rebasedChange == null) {
        break
      }
      rebasedChanges.add(rebasedChange)
      real = computeSnapshotAfter(rebasedChange)
    }
    return rebasedChanges
  }

  private fun rebaseElfTextChange(change: ElfTextChange, real: DocumentSnapshot, realChanges: List<RealTextChange>): ElfTextChange? {
    val changeEvent = change.changeEvent
    if (DocumentEventUtil.isMoveInsertion(changeEvent) || DocumentEventUtil.isMoveDeletion(changeEvent)) {
      // TODO: Rebase move insertion/deletion as one paired move. Rebasing either half independently can corrupt move offsets/text.
      return null
    }
    val startOffset = rebaseRangeStart(changeEvent.offset, changeEvent.oldLength, realChanges)
    if (startOffset == null) {
      return null
    }
    val endOffset = startOffset + changeEvent.oldLength
    if (startOffset < 0 || endOffset > real.textLength()) {
      return null
    }
    if (!matchesOldFragment(real.text(), startOffset, changeEvent.oldFragment)) {
      return null
    }
    val newWholeText = real.text().replace(startOffset, endOffset, changeEvent.newFragment)
    val initialStartOffset = if (changeEvent is DocumentEventImpl) {
      rebaseRangeStart(changeEvent.initialStartOffset, changeEvent.initialOldLength, realChanges) ?: return null
    } else {
      startOffset
    }
    val initialOldLength = if (changeEvent is DocumentEventImpl) changeEvent.initialOldLength else changeEvent.oldLength
    val rebasedEvent = DocumentEventImpl(
      changeEvent.document,
      startOffset,
      changeEvent.oldFragment,
      changeEvent.newFragment,
      real.modStamp(),
      changeEvent.isWholeTextReplaced,
      initialStartOffset,
      initialOldLength,
      startOffset,
      real.textLength(),
    )
    return ElfTextChange(
      real,
      rebasedEvent,
      newWholeText,
      DocumentModStamp.next(),
      change.clearLineFlags,
      change.isInBulkUpdate,
      change.project,
      change.commandName,
      change.commandGroupId,
      change.isTransparent,
    )
  }

  private fun computeSnapshotAfter(change: ElfTextChange): DocumentSnapshot {
    val changeEvent = change.changeEvent
    return change.snapshotBefore.withText(
      change.newWholeText,
      changeEvent.offset,
      changeEvent.offset + changeEvent.oldLength,
      changeEvent.newFragment,
      change.newModStamp,
      changeEvent.isWholeTextReplaced,
      change.clearLineFlags,
    )
  }

  private fun matchesOldFragment(wholeText: CharSequence, startOffset: Int, oldFragment: CharSequence): Boolean {
    val endOffset = startOffset + oldFragment.length
    return endOffset <= wholeText.length &&
           oldFragment.withIndex().all { (index, char) ->
             wholeText[startOffset + index] == char
           }
  }

  /**
   * Returns a range start offset shifted through already-applied [realChanges].
   * `null` means a real change overlaps the original elf range or inserts inside it, so this minimal rebase treats
   * the elf change as conflicting and leaves it, plus later elf changes, reverted.
   */
  private fun rebaseRangeStart(offset: Int, oldLength: Int, realChanges: List<RealTextChange>): Int? {
    var startOffset = offset
    var endOffset = startOffset + oldLength
    for (realTextChange in realChanges) {
      val realChange = realTextChange.changeEvent
      val realStartOffset = realChange.offset
      val realEndOffset = realStartOffset + realChange.oldLength
      val realChangeLengthDelta = realChange.newLength - realChange.oldLength
      if (realChange.oldLength == 0) {
        if (realStartOffset <= startOffset) {
          startOffset += realChangeLengthDelta
          endOffset += realChangeLengthDelta
        } else if (realStartOffset < endOffset) {
          return null
        }
      } else if (realEndOffset <= startOffset) {
        startOffset += realChangeLengthDelta
        endOffset += realChangeLengthDelta
      } else if (realStartOffset < endOffset) {
        return null
      }
    }
    return startOffset
  }

  private fun markClean() {
    ThreadingAssertions.assertEventDispatchThread()
    check(!Elf.getElf().isInElfScope()) {
      "unsafe to mark snapshot clean because ElfDocument is mutable within elf scope"
    }
    while (true) {
      val expect = getSnapshotSnapshot()
      val update = SnapshotSnapshot.newClean(expect.real)
      if (compareAndSet(expect, update)) {
        checkTextConsistency(expect)
        return
      }
    }
  }

  private fun checkTextConsistency(expect: SnapshotSnapshot) {
    val real = expect.real.text()
    val elf = expect.elf.text()
    check(real === elf || real.hashCode() == elf.hashCode()) {
      "inconsistent text detected, which leads to text change without notification to listeners"
    }
  }

  private fun assertOutsideElfScope() {
    if (Elf.getElf().isInElfScope()) {
      throw IllegalStateException("operation is forbidden inside elfScope")
    }
  }

  private fun <T : Any> LinkedBlockingQueue<T>.drainAll(): List<T> {
    val result = ArrayList<T>(size)
    drainTo(result)
    return result
  }

  private inner class SchedulerImpl : ElfDocumentSyncScheduler() {
    override fun sync() {
      this@ElfRealSync.applyElfChangesToReal()
    }
  }
}
