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
import com.intellij.openapi.editor.ex.DocumentRangeMarkerTree
import com.intellij.openapi.editor.ex.DocumentSettings
import com.intellij.openapi.editor.ex.DocumentSnapshot
import com.intellij.openapi.editor.impl.event.DocumentEventImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.ProperTextRange
import com.intellij.util.text.ImmutableCharSequence
import java.util.function.UnaryOperator
import kotlin.concurrent.Volatile

internal abstract class DocumentMutatorImpl(
  private val settings: DocumentSettings,
  private val dispatcher: DocumentEventDispatcherImpl,
  private val tree: DocumentRangeMarkerTree,
) : DocumentMutator {
  @Volatile private var textChangeInProgress = false

  protected abstract fun getSnapshot(): DocumentSnapshot
  protected abstract fun updateAndGet(update: UnaryOperator<DocumentSnapshot>): DocumentSnapshot

  override fun setModStamp(newModStamp: Long, incrementModSequence: Boolean) {
    updateAndGet { it.withModStamp(newModStamp, incrementModSequence) }
  }

  override fun clearLineFlags(startLine: Int, endLine: Int, exceptLines: IntArray) {
    updateAndGet { it.withClearedLineFlags(startLine, endLine, exceptLines) }
  }

  override fun insertString(
    hostDocument: Document,
    insertOffset: Int,
    insertString: CharSequence,
  ) {
    val snapshot = getSnapshot()
    assertBounds(insertOffset, snapshot.textLength())
    assertWriteAccess(hostDocument)
    assertValidSeparators(insertString)
    if (insertString.isEmpty()) {
      return
    }
    val oldWholeText: ImmutableCharSequence = snapshot.text()
    val newWholeText: ImmutableCharSequence = oldWholeText.insert(insertOffset, insertString)
    val newFragment: CharSequence = newWholeText.subtext(insertOffset, insertOffset + insertString.length)
    changeText(
      hostDocument,
      snapshot,
      newWholeText,
      insertOffset,
      insertOffset,
      insertOffset,
      "",
      newFragment,
      false,
      false,
      DocumentModStamp.next(),
      insertOffset,
      0,
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
      snapshot.textLength(),
      0,
      newWholeText,
      newModStamp,
      true,
      true,
    )
  }

  override fun setText(hostDocument: Document, newWholeText: CharSequence) {
    val snapshot = getSnapshot()
    executeCommandIfNeeded(
      hostDocument,
      Runnable {
        replaceString(
          hostDocument,
          snapshot,
          0,
          snapshot.textLength(),
          0,
          newWholeText,
          DocumentModStamp.next(),
          true,
          true,
        )
      }
    )
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
    val replacement = snapshot.string(srcRange)
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
    wholeTextReplaced: Boolean,
  ) {
    replaceString(
      hostDocument,
      getSnapshot(),
      startOffset,
      endOffset,
      moveOffset,
      replaceString,
      newModStamp,
      wholeTextReplaced,
      false,
    )
  }

  private fun deleteString(
    hostDocument: Document,
    snapshot: DocumentSnapshot,
    startOffset: Int,
    endOffset: Int,
  ): DocumentSnapshot {
    assertBounds(snapshot, startOffset, endOffset)
    assertWriteAccess(hostDocument)
    if (startOffset == endOffset) {
      return snapshot
    }
    val oldText: ImmutableCharSequence = snapshot.text()
    val newText: ImmutableCharSequence = oldText.delete(startOffset, endOffset)
    val oldString: CharSequence = oldText.subtext(startOffset, endOffset)
    return changeText(
      hostDocument,
      snapshot,
      newText,
      startOffset,
      endOffset,
      startOffset,
      oldString,
      "",
      false,
      false,
      DocumentModStamp.next(),
      startOffset,
      endOffset - startOffset,
    )
  }

  private fun replaceString(
    hostDocument: Document,
    snapshot: DocumentSnapshot,
    startOffset: Int,
    endOffset: Int,
    moveOffset: Int,
    s: CharSequence,
    newModStamp: Long,
    wholeText: Boolean,
    clearLineFlags: Boolean,
  ): DocumentSnapshot {
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
      snapshot.text(),
      startOffset,
      endOffset,
      moveOffset,
      s,
      wholeText,
    )
    if (replacement.perform()) {
      return snapshot
    }
    return changeText(
      hostDocument,
      snapshot,
      replacement.newWholeText,
      replacement.startOffset,
      replacement.endOffset,
      replacement.moveOffset,
      replacement.oldFragment,
      replacement.newFragment,
      replacement.isWholeTextReplaced,
      clearLineFlags,
      newModStamp,
      replacement.initialStartOffset,
      replacement.initialOldLength,
    )
  }

  private fun changeText(
    hostDocument: Document,
    snapshotBefore: DocumentSnapshot,
    newWholeText: ImmutableCharSequence,
    startOffset: Int,
    endOffset: Int,
    moveOffset: Int,
    oldFragment: CharSequence,
    newFragment: CharSequence,
    wholeTextReplaced: Boolean,
    clearLineFlags: Boolean,
    newModStamp: Long,
    initialStartOffset: Int,
    initialOldLength: Int,
  ): DocumentSnapshot {
    val changeEvent: DocumentEvent = DocumentEventImpl(
      hostDocument,
      startOffset,
      oldFragment,
      newFragment,
      snapshotBefore.modStamp(),
      wholeTextReplaced,
      initialStartOffset,
      initialOldLength,
      moveOffset,
      snapshotBefore.textLength(),
    )
    assertChangeAllowed(changeEvent, endOffset, snapshotBefore.textLength())
    val snapshotAfter = changeText(
      snapshotBefore,
      changeEvent,
      newWholeText,
      newModStamp,
      clearLineFlags,
    )
    if (newFragment.length > oldFragment.length) {
      return trimToSize(hostDocument, snapshotAfter)
    }
    return snapshotAfter
  }

  protected open fun changeText(
    snapshotBefore: DocumentSnapshot,
    changeEvent: DocumentEvent,
    newWholeText: ImmutableCharSequence,
    newModStamp: Long,
    clearLineFlags: Boolean,
  ): DocumentSnapshot {
    assertNotNestedModification()
    val snapshotAfterChange: DocumentSnapshot
    textChangeInProgress = true
    try {
      snapshotAfterChange = dispatcher.withFiringTextUpdate(changeEvent) {
        updateAndGet { latest ->
          // modStamp or other metadata could be changed during fireBeforeTextChange,
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
          )
        }
      }
    } finally {
      textChangeInProgress = false
    }
    return snapshotAfterChange
  }

  private fun trimToSize(hostDocument: Document, snapshot: DocumentSnapshot): DocumentSnapshot {
    val bufferSize = settings.cycleBufferSize()
    if (bufferSize > 0 && snapshot.textLength() > bufferSize) {
      return deleteString(hostDocument, snapshot, 0, snapshot.textLength() - bufferSize)
    }
    return snapshot
  }

  private fun executeCommandIfNeeded(hostDocument: Document, runnable: Runnable) {
    if (!settings.isCommandCheckEnabled()) {
      runnable.run()
      return
    }
    val commandProcessor = CommandProcessor.getInstance()
    if (commandProcessor.isUndoTransparentActionInProgress) {
      runnable.run()
    } else {
      commandProcessor.executeCommand(
        null,
        runnable,
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
      val marker: RangeMarker? = tree.getRangeGuard(changeEvent.getOffset(), endOffset)
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
  private fun assertBounds(snapshot: DocumentSnapshot, startOffset: Int, endOffset: Int) {
    val textLength = snapshot.textLength()
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
