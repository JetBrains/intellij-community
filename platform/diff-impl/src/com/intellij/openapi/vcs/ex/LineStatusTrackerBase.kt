/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vcs.ex

import com.intellij.diff.util.DiffUtil
import com.intellij.diff.util.DiffUtil.executeWriteCommand
import com.intellij.diff.util.Side
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.command.undo.UndoUtil
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vcs.ex.DocumentTracker.Block
import com.intellij.openapi.vcs.ex.LineStatusTrackerBlockOperations.Companion.isSelectedByLine
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresEdt
import java.util.*

abstract class LineStatusTrackerBase<R : Range>(
  override val project: Project?,
  final override val document: Document
) : LineStatusTrackerI<R> {
  final override val vcsDocument: Document = createVcsDocument(document)

  final override val disposable: Disposable = Disposer.newDisposable()
  internal val LOCK: DocumentTracker.Lock = DocumentTracker.Lock()

  protected val blockOperations: LineStatusTrackerBlockOperations<R, Block> = MyBlockOperations(LOCK)
  protected val documentTracker: DocumentTracker
  protected abstract val renderer: LineStatusMarkerRenderer

  final override var isReleased: Boolean = false
    private set

  protected var isInitialized: Boolean = false
    private set

  protected val blocks: List<Block> get() = documentTracker.blocks

  init {
    documentTracker = DocumentTracker(vcsDocument, document, LOCK)
    Disposer.register(disposable, documentTracker)

    documentTracker.addHandler(MyDocumentTrackerHandler())
  }


  @RequiresEdt
  protected open fun isDetectWhitespaceChangedLines(): Boolean = false

  /**
   * Prevent "trim trailing spaces for modified lines on save" from re-applying just reverted changes.
   */
  protected open fun isClearLineModificationFlagOnRollback(): Boolean = false

  protected abstract fun toRange(block: Block): R


  override val virtualFile: VirtualFile? get() = null

  override fun getRanges(): List<R>? {
    ApplicationManager.getApplication().assertReadAccessAllowed()
    return blockOperations.getRanges()
  }

  @RequiresEdt
  protected open fun setBaseRevisionContent(vcsContent: CharSequence, beforeUnfreeze: (() -> Unit)?) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    if (isReleased) return

    documentTracker.doFrozen(Side.LEFT) {
      updateDocument(Side.LEFT) {
        vcsDocument.setText(vcsContent)
      }

      beforeUnfreeze?.invoke()
    }

    if (!isInitialized) {
      isInitialized = true
      updateHighlighters()
    }
  }

  @RequiresEdt
  fun dropBaseRevision() {
    ApplicationManager.getApplication().assertIsDispatchThread()
    if (isReleased || !isInitialized) return

    isInitialized = false
    updateHighlighters()

    documentTracker.doFrozen {
      updateDocument(Side.LEFT) {
        vcsDocument.setText(document.immutableCharSequence)
        documentTracker.setFrozenState(emptyList())
      }
    }
  }

  fun release() {
    val runnable = Runnable {
      if (isReleased) return@Runnable
      isReleased = true

      Disposer.dispose(disposable)
    }

    if (!ApplicationManager.getApplication().isDispatchThread || LOCK.isHeldByCurrentThread) {
      ApplicationManager.getApplication().invokeLater(runnable)
    }
    else {
      runnable.run()
    }
  }


  @RequiresEdt
  protected fun updateDocument(side: Side, task: (Document) -> Unit): Boolean {
    return updateDocument(side, null, task)
  }

  @RequiresEdt
  protected fun updateDocument(side: Side, commandName: @NlsContexts.Command String?, task: (Document) -> Unit): Boolean {
    val affectedDocument = if (side.isLeft) vcsDocument else document
    return updateDocument(project, affectedDocument, commandName, task)
  }

  @RequiresEdt
  override fun doFrozen(task: Runnable) {
    documentTracker.doFrozen({ task.run() })
  }

  override fun <T> readLock(task: () -> T): T {
    return documentTracker.readLock(task)
  }


  private inner class MyBlockOperations(lock: DocumentTracker.Lock) : LineStatusTrackerBlockOperations<R, Block>(lock) {
    override fun getBlocks(): List<Block>? = if (isValid()) blocks else null
    override fun Block.toRange(): R = toRange(this)
  }

  private inner class MyDocumentTrackerHandler : DocumentTracker.Handler {
    override fun afterBulkRangeChange(isDirty: Boolean) {
      updateHighlighters()
    }

    override fun onUnfreeze(side: Side) {
      updateHighlighters()
    }
  }

  protected abstract inner class InnerRangesDocumentTrackerHandler : DocumentTracker.Handler {
    abstract var Block.innerRanges: List<Range.InnerRange>?

    abstract fun isDetectWhitespaceChangedLines(): Boolean


    override fun onRangeShifted(before: Block, after: Block) {
      after.innerRanges = before.innerRanges
    }

    override fun afterBulkRangeChange(isDirty: Boolean) {
      if (!isDirty) updateMissingInnerRanges()
    }

    override fun onUnfreeze(side: Side) {
      updateMissingInnerRanges()
    }

    private fun updateMissingInnerRanges() {
      if (!isDetectWhitespaceChangedLines()) return
      if (documentTracker.isFrozen()) return

      for (block in blocks) {
        if (block.innerRanges == null) {
          block.innerRanges = calcInnerRanges(block)
        }
      }
    }

    @RequiresEdt
    fun resetInnerRanges() {
      LOCK.write {
        if (isDetectWhitespaceChangedLines()) {
          for (block in blocks) {
            block.innerRanges = calcInnerRanges(block)
          }
        }
        else {
          for (block in blocks) {
            block.innerRanges = null
          }
        }
      }
    }

    private fun calcInnerRanges(block: Block): List<Range.InnerRange>? {
      if (block.start == block.end || block.vcsStart == block.vcsEnd) return null
      return createInnerRanges(block.range,
                               vcsDocument.immutableCharSequence, document.immutableCharSequence,
                               vcsDocument.lineOffsets, document.lineOffsets)
    }
  }

  protected fun updateHighlighters() {
    renderer.scheduleUpdate()
  }


  override fun isOperational(): Boolean = LOCK.read {
    return isInitialized && !isReleased
  }

  override fun isValid(): Boolean = LOCK.read {
    return isOperational() && !documentTracker.isFrozen()
  }

  override fun findRange(range: Range): R? = blockOperations.findRange(range)
  override fun getNextRange(line: Int): R? = blockOperations.getNextRange(line)
  override fun getPrevRange(line: Int): R? = blockOperations.getPrevRange(line)
  override fun getRangesForLines(lines: BitSet): List<R>? = blockOperations.getRangesForLines(lines)
  override fun getRangeForLine(line: Int): R? = blockOperations.getRangeForLine(line)
  override fun isLineModified(line: Int): Boolean = blockOperations.isLineModified(line)
  override fun isRangeModified(startLine: Int, endLine: Int): Boolean = blockOperations.isRangeModified(startLine, endLine)
  override fun transferLineFromVcs(line: Int, approximate: Boolean): Int = blockOperations.transferLineFromVcs(line, approximate)
  override fun transferLineToVcs(line: Int, approximate: Boolean): Int = blockOperations.transferLineToVcs(line, approximate)


  @RequiresEdt
  override fun rollbackChanges(range: Range) {
    val newRange = blockOperations.findBlock(range)
    if (newRange != null) {
      runBulkRollback { it == newRange }
    }
  }

  @RequiresEdt
  override fun rollbackChanges(lines: BitSet) {
    runBulkRollback { it.isSelectedByLine(lines) }
  }

  @RequiresEdt
  protected fun runBulkRollback(condition: (Block) -> Boolean) {
    if (!isValid()) return

    updateDocument(Side.RIGHT, DiffBundle.message("rollback.change.command.name")) {
      documentTracker.partiallyApplyBlocks(Side.RIGHT, condition) { block, shift ->
        fireLinesUnchanged(block.start + shift, block.start + shift + (block.vcsEnd - block.vcsStart))
      }
    }
  }

  private fun fireLinesUnchanged(startLine: Int, endLine: Int) {
    if (isClearLineModificationFlagOnRollback()) {
      DiffUtil.clearLineModificationFlags(document, startLine, endLine)
    }
  }


  companion object {
    @JvmStatic
    protected val LOG: Logger = Logger.getInstance(LineStatusTrackerBase::class.java)
    private val VCS_DOCUMENT_KEY: Key<Boolean> = Key.create("LineStatusTrackerBase.VCS_DOCUMENT_KEY")
    val SEPARATE_UNDO_STACK: Key<Boolean> = Key.create("LineStatusTrackerBase.SEPARATE_UNDO_STACK")

    fun createVcsDocument(originalDocument: Document): Document {
      val result = DocumentImpl(originalDocument.immutableCharSequence, true)
      UndoUtil.disableUndoFor(result)
      result.putUserData(VCS_DOCUMENT_KEY, true)
      result.setReadOnly(true)
      return result
    }

    @RequiresEdt
    fun updateDocument(project: Project?,
                       document: Document,
                       commandName: @NlsContexts.Command String?,
                       task: (Document) -> Unit): Boolean {
      if (DiffUtil.isUserDataFlagSet(VCS_DOCUMENT_KEY, document)) {
        document.setReadOnly(false)
        try {
          CommandProcessor.getInstance().runUndoTransparentAction {
            task(document)
          }
          return true
        }
        finally {
          document.setReadOnly(true)
        }
      }
      else {
        val isSeparateUndoStack = DiffUtil.isUserDataFlagSet(SEPARATE_UNDO_STACK, document)
        return executeWriteCommand(project, document, commandName, null, UndoConfirmationPolicy.DEFAULT, false,
                                   !isSeparateUndoStack) { task(document) }
      }
    }
  }


  override fun toString(): String {
    return javaClass.name + "(" +
           "file=" +
           virtualFile?.let { file ->
             file.path +
             (if (!file.isInLocalFileSystem) "@$file" else "") +
             "@" + Integer.toHexString(file.hashCode())
           } +
           ", document=" +
           document.let { doc ->
             doc.toString() +
             "@" + Integer.toHexString(doc.hashCode())
           } +
           ", isReleased=$isReleased" +
           ")" +
           "@" + Integer.toHexString(hashCode())
  }
}
