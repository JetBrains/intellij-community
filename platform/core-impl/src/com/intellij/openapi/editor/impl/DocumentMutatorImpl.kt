// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.ReadOnlyFragmentModificationException
import com.intellij.openapi.editor.actionSystem.DocCommandGroupId
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.ex.DocumentMutator
import com.intellij.openapi.editor.ex.DocumentOp
import com.intellij.openapi.editor.ex.DocumentPatch
import com.intellij.openapi.editor.ex.DocumentSettings
import com.intellij.openapi.editor.ex.experimental.DocumentBranch
import com.intellij.openapi.editor.impl.event.DocumentEventImpl
import com.intellij.openapi.editor.impl.marker.SnapshotMarkerStores
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.ProperTextRange
import com.intellij.util.text.ImmutableCharSequence
import kotlin.concurrent.Volatile

internal abstract class DocumentMutatorImpl(
  private val settings: DocumentSettings,
  private val dispatcher: DocumentEventDispatcherImpl,
  val snapshotMarkerStores: SnapshotMarkerStores,
) : DocumentMutator {
  @Volatile private var textChangeInProgress: Boolean = false

  protected abstract fun getSnapshot(): DocumentBranch
  protected abstract fun updateAndGet(
    initialSnapshot: DocumentBranch? = null,
    patch: DocumentPatch? = null,
    update: (DocumentBranch) -> DocumentBranch,
  ): DocumentBranch

  override fun setModStamp(newModStamp: Long, incrementModSequence: Boolean) {
    val op = DocumentOp.modStampOp(newModStamp, incrementModSequence)
    updateAndGet { it.applyOp(op) }
  }

  override fun clearLineFlags(startLine: Int, endLine: Int, exceptLines: IntArray) {
    val op = DocumentOp.cleanLinesOp(startLine, endLine, exceptLines)
    updateAndGet { it.applyOp(op) }
  }

  override fun insertString(
    hostDocument: Document,
    insertOffset: Int,
    insertString: CharSequence,
  ) {
    val snapshot = getSnapshot()
    assertBounds(insertOffset, snapshot.text().length())
    assertWriteAccess(hostDocument)
    assertValidSeparators(insertString)
    if (insertString.isEmpty()) {
      return
    }
    val newFragment: CharSequence = ImmutableCharSequence.asImmutable(insertString)
    changeText(
      hostDocument,
      snapshot,
      DocumentPatch.simple(
        startOffset = insertOffset,
        endOffset = insertOffset,
        newFragment = newFragment,
        newModStamp = DocumentModStamp.next(),
        clearLineFlags = false,
      ),
      insertOffset, // TODO: refactor excessive params
      insertOffset,
      "",
      newFragment,
      false,
    )
  }

  override fun deleteString(
    hostDocument: Document,
    startOffset: Int,
    endOffset: Int,
  ) {
    deleteString(hostDocument, getSnapshot(), startOffset, endOffset)
  }

  override fun replaceText(
    hostDocument: Document,
    newWholeText: CharSequence,
    newModStamp: Long,
  ) {
    val snapshot = getSnapshot()
    replaceString(
      hostDocument,
      snapshot,
      0,
      snapshot.text().length(),
      0,
      newWholeText,
      newModStamp,
      true,
    )
  }

  override fun setText(hostDocument: Document, newWholeText: CharSequence) {
    val snapshot = getSnapshot()
    executeCommandIfNeeded(hostDocument) {
      replaceString(
        hostDocument,
        snapshot,
        0,
        snapshot.text().length(),
        0,
        newWholeText,
        DocumentModStamp.next(),
        true,
      )
    }
  }

  override fun moveText(
    hostDocument: Document,
    srcStartOffset: Int,
    srcEndOffset: Int,
    dstOffset: Int,
  ) {
    val snapshot = getSnapshot()
    assertBounds(snapshot, srcStartOffset, srcEndOffset)
    if (dstOffset == srcStartOffset || dstOffset == srcEndOffset) {
      return
    }
    val srcRange = ProperTextRange(srcStartOffset, srcEndOffset)
    assert(!srcRange.containsOffset(dstOffset)) {
      "Can't perform text move from range [$srcStartOffset; $srcEndOffset) to offset $dstOffset"
    }
    val replacement = snapshot.text().string(srcStartOffset, srcEndOffset)
    val shift = if (dstOffset < srcStartOffset) srcEndOffset - srcStartOffset else 0
    // a pair of insert/remove modifications
    val newSnapshot = replaceString(
      hostDocument,
      snapshot,
      dstOffset,
      dstOffset,
      srcStartOffset + shift,
      replacement,
      DocumentModStamp.next(),
      false,
    )
    replaceString(
      hostDocument,
      newSnapshot,
      srcStartOffset + shift,
      srcEndOffset + shift,
      dstOffset,
      "",
      DocumentModStamp.next(),
      false,
    )
  }

  override fun replaceString(
    hostDocument: Document,
    startOffset: Int,
    endOffset: Int,
    moveOffset: Int,
    replaceString: CharSequence,
    newModStamp: Long,
  ) {
    replaceString(
      hostDocument,
      getSnapshot(),
      startOffset,
      endOffset,
      moveOffset,
      replaceString,
      newModStamp,
      false,
    )
  }

  private fun deleteString(
    hostDocument: Document,
    snapshot: DocumentBranch,
    startOffset: Int,
    endOffset: Int,
  ): DocumentBranch {
    assertBounds(snapshot, startOffset, endOffset)
    assertWriteAccess(hostDocument)
    if (startOffset == endOffset) {
      return snapshot
    }
    val oldText: ImmutableCharSequence = snapshot.text().chars()
    val oldString: CharSequence = oldText.subtext(startOffset, endOffset)
    return changeText(
      hostDocument,
      snapshot,
      DocumentPatch.simple(
        startOffset = startOffset,
        endOffset = endOffset,
        newFragment = "",
        newModStamp = DocumentModStamp.next(),
        clearLineFlags = false,
      ),
      startOffset, // TODO: refactor excessive params
      endOffset,
      oldString,
      "",
      false,
    )
  }

  private fun replaceString(
    hostDocument: Document,
    snapshot: DocumentBranch,
    startOffset: Int,
    endOffset: Int,
    moveOffset: Int,
    s: CharSequence,
    newModStamp: Long,
    wholeTextReplaced: Boolean,
  ): DocumentBranch {
    assertBounds(snapshot, startOffset, endOffset)
    assertWriteAccess(hostDocument)
    assertValidSeparators(s)
    if (moveOffset != startOffset && startOffset != endOffset && s.isNotEmpty()) {
      throw IllegalArgumentException(
        "moveOffset != startOffset for a modification which is neither an insert nor deletion." +
        " startOffset: $startOffset; endOffset: $endOffset; moveOffset: $moveOffset;"
      )
    }
    val replacement = OptimizedTextReplacement(
      snapshot.text().chars(),
      startOffset,
      endOffset,
      moveOffset,
      s,
      wholeTextReplaced,
      newModStamp,
    )
    if (replacement.perform()) {
      return snapshot
    }
    return changeText(
      hostDocument,
      snapshot,
      replacement.patch,
      replacement.startOffset,
      replacement.endOffset,
      replacement.oldFragment,
      replacement.newFragment,
      replacement.isWholeTextReplaced,
    )
  }

  private fun changeText(
    hostDocument: Document,
    snapshotBefore: DocumentBranch,
    patch: DocumentPatch,
    startOffset: Int,
    endOffset: Int,
    oldFragment: CharSequence,
    newFragment: CharSequence,
    wholeTextReplaced: Boolean,
  ): DocumentBranch {
    val textBefore = snapshotBefore.text()
    val changeEvent: DocumentEvent = DocumentEventImpl(
      hostDocument,
      startOffset,
      oldFragment,
      newFragment,
      snapshotBefore.modState().stamp(),
      wholeTextReplaced,
      patch.originStartOffset(),
      patch.originEndOffset() - patch.originStartOffset(),
      patch.moveOffset(),
      textBefore.length(),
    )
    assertChangeAllowed(changeEvent, endOffset, textBefore.length())
    val snapshotAfter = changeText(snapshotBefore, changeEvent, patch)
    if (newFragment.length > oldFragment.length) {
      return trimToSize(hostDocument, snapshotAfter)
    }
    return snapshotAfter
  }

  private fun changeText(
    snapshotBefore: DocumentBranch,
    changeEvent: DocumentEvent,
    patch: DocumentPatch,
  ): DocumentBranch {
    if (changeEvent is DocumentEventImpl) {
      patch.attachLineDiff(changeEvent.lineDiff)
    }
    assertNotNestedModification()
    val snapshotAfterChange: DocumentBranch
    textChangeInProgress = true
    try {
      snapshotAfterChange = dispatcher.withFiringTextUpdate(changeEvent) {
        updateAndGet(snapshotBefore, patch) { applyPatch(it, patch) }
      }
    } finally {
      textChangeInProgress = false
    }
    return snapshotAfterChange
  }

  private fun applyPatch(before: DocumentBranch, patch: DocumentPatch): DocumentBranch {
    var after = before
    for (op in patch.ops()) {
      after = after.applyOp(op)
    }
    return after
  }

  private fun trimToSize(hostDocument: Document, snapshot: DocumentBranch): DocumentBranch {
    val bufferSize = settings.cycleBufferSize()
    if (bufferSize <= 0) {
      return snapshot
    }
    val length = snapshot.text().length()
    if (length > bufferSize) {
      return deleteString(hostDocument, snapshot, 0, length - bufferSize)
    }
    return snapshot
  }

  private inline fun executeCommandIfNeeded(hostDocument: Document, crossinline command: () -> Unit) {
    if (!settings.isCommandCheckEnabled()) {
      command.invoke()
      return
    }
    val commandProcessor = CommandProcessor.getInstance()
    if (commandProcessor.isUndoTransparentActionInProgress || commandProcessor.isCommandInProgress) {
      command.invoke()
    } else {
      commandProcessor.executeCommand(
        null,
        Runnable { command.invoke() },
        "",
        DocCommandGroupId.noneGroupId(hostDocument),
      )
    }
  }

  private fun assertWriteAccess(hostDocument: Document) {
    settings.assertWriteAccess(hostDocument)
    settings.assertWritable(hostDocument)
  }

  private fun assertValidSeparators(s: CharSequence) {
    settings.assertValidSeparators(s)
  }

  private fun assertNotNestedModification() {
    if (textChangeInProgress) {
      throw IllegalStateException("Detected document modification from DocumentListener")
    }
  }

  private fun assertChangeAllowed(
    changeEvent: DocumentEvent,
    endOffset: Int,
    textLength: Int,
  ) {
    assertFragmentNotGuarded(changeEvent, endOffset)
    assertMoveOffsetValid(changeEvent.moveOffset, textLength)
    assertFileValid(changeEvent.document)
    assertInsideCommand()
  }

  private fun assertBounds(offset: Int, textLength: Int) {
    if (offset < 0) {
      throw IndexOutOfBoundsException("Wrong offset: $offset")
    }
    if (offset > textLength) {
      throw IndexOutOfBoundsException(
        "Wrong offset: $offset; documentLength: $textLength"
      )
    }
  }

  private fun assertInsideCommand() {
    settings.assertInsideCommand()
  }

  private fun assertFragmentNotGuarded(changeEvent: DocumentEvent, endOffset: Int) {
    if (settings.isGuardCheckEnabled(changeEvent.isWholeTextReplaced)) {
      val marker: RangeMarker? = changeEvent.document.getRangeGuard(changeEvent.getOffset(), endOffset)
      if (marker != null) {
        throw ReadOnlyFragmentModificationException(changeEvent, marker)
      }
    }
  }

  @Suppress("ConvertTwoComparisonsToRangeCheck")
  private fun assertMoveOffsetValid(moveOffset: Int, textLength: Int) {
    assert(moveOffset >= 0 && moveOffset <= textLength) {
      "Invalid moveOffset: $moveOffset"
    }
  }

  private fun assertFileValid(hostDocument: Document) {
    val app = ApplicationManager.getApplication()
    if (app != null) {
      val manager = FileDocumentManager.getInstance()
      val file = manager.getFile(hostDocument)
      if (file != null && !file.isValid()) {
        LOG.error("File of this document has been deleted: $file")
      }
    }
  }

  @Suppress("ConvertTwoComparisonsToRangeCheck")
  private fun assertBounds(snapshot: DocumentBranch, startOffset: Int, endOffset: Int) {
    val textLength = snapshot.text().length()
    if (startOffset < 0 || startOffset > textLength) {
      throw IndexOutOfBoundsException("Wrong startOffset: $startOffset; documentLength: $textLength")
    }
    if (endOffset < 0 || endOffset > textLength) {
      throw IndexOutOfBoundsException("Wrong endOffset: $endOffset; documentLength: $textLength")
    }
    if (endOffset < startOffset) {
      throw IllegalArgumentException(
        "endOffset < startOffset: $endOffset < $startOffset; documentLength: $textLength"
      )
    }
  }

  companion object {
    private val LOG: Logger = logger<DocumentMutatorImpl>()
  }
}
