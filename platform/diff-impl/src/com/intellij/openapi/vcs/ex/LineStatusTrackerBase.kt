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
import com.intellij.diff.util.Side
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.undo.UndoConstants
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.localVcs.UpToDateLineNumberProvider.ABSENT_LINE_NUMBER
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.ex.DocumentTracker.Block
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.nullize
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.annotations.TestOnly
import java.util.*

abstract class LineStatusTrackerBase<R : Range> {
  protected val application: Application = ApplicationManager.getApplication()

  open val project: Project?

  val document: Document
  val vcsDocument: Document

  protected val disposable = Disposer.newDisposable()
  protected val documentTracker: DocumentTracker
  protected abstract val renderer: LineStatusMarkerRenderer

  var isReleased: Boolean = false
    private set

  private var isInitialized: Boolean = false

  protected val blocks: List<Block> get() = documentTracker.blocks
  internal val LOCK: DocumentTracker.Lock get() = documentTracker.LOCK

  constructor(project: Project?, document: Document) {
    this.project = project
    this.document = document

    vcsDocument = DocumentImpl(this.document.immutableCharSequence)
    vcsDocument.putUserData(UndoConstants.DONT_RECORD_UNDO, true)
    vcsDocument.setReadOnly(true)

    documentTracker = DocumentTracker(vcsDocument, this.document)
    documentTracker.addListener(MyLineTrackerListener())
    Disposer.register(disposable, documentTracker)
  }


  @CalledInAwt
  protected open fun isDetectWhitespaceChangedLines(): Boolean = false

  @CalledInAwt
  protected open fun fireFileUnchanged() {}

  protected open fun fireLinesUnchanged(startLine: Int, endLine: Int) {}

  open val virtualFile: VirtualFile? get() = null

  abstract protected fun Block.toRange(): R


  fun getRanges(): List<R>? {
    application.assertReadAccessAllowed()
    LOCK.read {
      if (!isValid()) return null
      return blocks.filter { !it.range.isEmpty }.map { it.toRange() }
    }
  }

  @CalledInAwt
  fun setBaseRevision(vcsContent: CharSequence) {
    application.assertIsDispatchThread()
    if (isReleased) return

    documentTracker.doFrozen(Side.LEFT) {
      updateDocument(Side.LEFT) {
        vcsDocument.setText(vcsContent)
      }
    }

    if (!isInitialized) {
      isInitialized = true
      updateHighlighters()
    }
  }

  @CalledInAwt
  fun dropBaseRevision() {
    application.assertIsDispatchThread()
    if (isReleased) return

    isInitialized = false
    updateHighlighters()
  }

  @CalledInAwt
  protected fun updateDocument(side: Side, task: (Document) -> Unit): Boolean {
    return updateDocument(side, null, task)
  }

  @CalledInAwt
  protected fun updateDocument(side: Side, commandName: String?, task: (Document) -> Unit): Boolean {
    val doc = side[vcsDocument, document]

    if (side.isLeft) doc.setReadOnly(false)
    try {
      return DiffUtil.executeWriteCommand(doc, project, commandName, { task(doc) })
    }
    finally {
      if (side.isLeft) doc.setReadOnly(true)
    }
  }

  @CalledInAwt
  fun doFrozen(task: Runnable) {
    documentTracker.doFrozen({ task.run() })
  }


  fun release() {
    val runnable = Runnable {
      if (isReleased) return@Runnable
      isReleased = true

      updateHighlighters()
      Disposer.dispose(disposable)
    }

    if (!application.isDispatchThread || LOCK.isHeldByCurrentThread) {
      application.invokeLater(runnable)
    }
    else {
      runnable.run()
    }
  }


  private inner class MyLineTrackerListener : DocumentTracker.Listener {
    override fun onRangeRemoved(block: Block) {
      destroyHighlighter(block)
    }

    override fun onRangeShifted(before: Block, after: Block) {
      after.ourData.innerRanges = before.ourData.innerRanges
    }

    override fun afterRefresh() {
      checkIfFileUnchanged()
      calcInnerRanges()
      installMissingHighlighters()
    }

    override fun afterRangeChange() {
      installMissingHighlighters()
    }

    override fun afterExplicitChange() {
      checkIfFileUnchanged()
      calcInnerRanges()
      installMissingHighlighters()
    }

    override fun onUnfreeze(side: Side) {
      installMissingHighlighters()
    }

    private fun checkIfFileUnchanged() {
      if (blocks.isEmpty()) {
        fireFileUnchanged()
      }
    }

    private fun calcInnerRanges() {
      if (isDetectWhitespaceChangedLines()) {
        for (block in blocks) {
          if (block.ourData.innerRanges == null) {
            block.ourData.innerRanges = calcInnerRanges(block)
            destroyHighlighter(block)
          }
        }
      }
    }

    private fun installMissingHighlighters() {
      for (block in blocks) {
        installHighlighter(block)
      }
    }
  }

  private fun calcInnerRanges(block: Block): List<Range.InnerRange> {
    if (block.start == block.end || block.vcsStart == block.vcsEnd) return emptyList()
    return createInnerRanges(block.range,
                             vcsDocument.immutableCharSequence, document.immutableCharSequence,
                             vcsDocument.lineOffsets, document.lineOffsets)
  }

  @CalledInAwt
  protected fun updateHighlighters() {
    LOCK.write {
      for (block in blocks) {
        updateHighlighter(block)
      }
    }
  }

  @CalledInAwt
  protected fun updateHighlighter(block: Block) {
    LOCK.write {
      destroyHighlighter(block)
      installHighlighter(block)
    }
  }

  @CalledInAwt
  protected fun updateInnerRanges() {
    LOCK.write {
      if (isDetectWhitespaceChangedLines()) {
        for (block in blocks) {
          block.ourData.innerRanges = calcInnerRanges(block)
        }
      }
      else {
        for (block in blocks) {
          block.ourData.innerRanges = null
        }
      }

      updateHighlighters()
    }
  }

  @CalledInAwt
  private fun installHighlighter(block: Block) {
    if (block.ourData.rangeHighlighter != null) return
    if (!isValid() || block.range.isEmpty) return
    try {
      block.ourData.rangeHighlighter = renderer.createHighlighter(block.toRange())
    }
    catch (e: Exception) {
      LOG.error(e)
    }
  }

  @CalledInAwt
  private fun destroyHighlighter(block: Block) {
    val highlighter = block.ourData.rangeHighlighter ?: return
    try {
      block.ourData.rangeHighlighter = null
      highlighter.dispose()
    }
    catch (e: Exception) {
      LOG.error(e)
    }
  }

  fun isOperational(): Boolean = LOCK.read {
    return isInitialized && !isReleased
  }

  fun isValid(): Boolean = LOCK.read {
    return isOperational() && !documentTracker.isFrozen()
  }


  fun findRange(range: Range): R? = findBlock(range)?.toRange()

  protected fun findBlock(range: Range): Block? {
    LOCK.read {
      if (!isValid()) return null
      for (block in blocks) {
        if (block.start == range.line1 &&
            block.end == range.line2 &&
            block.vcsStart == range.vcsLine1 &&
            block.vcsEnd == range.vcsLine2) {
          return block
        }
      }
      return null
    }
  }

  fun getNextRange(line: Int): R? {
    LOCK.read {
      if (!isValid()) return null
      for (block in blocks) {
        if (line < block.end && !block.isSelectedByLine(line)) {
          return block.toRange()
        }
      }
      return null
    }
  }

  fun getPrevRange(line: Int): R? {
    LOCK.read {
      if (!isValid()) return null
      for (block in blocks.reversed()) {
        if (line > block.start && !block.isSelectedByLine(line)) {
          return block.toRange()
        }
      }
      return null
    }
  }

  fun getRangesForLines(lines: BitSet): List<R>? {
    LOCK.read {
      if (!isValid()) return null
      val result = ArrayList<R>()
      for (block in blocks) {
        if (block.isSelectedByLine(lines)) {
          result.add(block.toRange())
        }
      }
      return result
    }
  }

  fun getRangeForLine(line: Int): R? {
    LOCK.read {
      if (!isValid()) return null
      for (block in blocks) {
        if (block.isSelectedByLine(line)) {
          return block.toRange()
        }
      }
      return null
    }
  }


  @CalledInAwt
  fun rollbackChanges(range: Range) {
    val newRange = findBlock(range)
    if (newRange != null) {
      runBulkRollback { it == newRange }
    }
  }

  @CalledInAwt
  fun rollbackChanges(lines: BitSet) {
    runBulkRollback { it.isSelectedByLine(lines) }
  }

  @CalledInAwt
  protected fun runBulkRollback(condition: (Block) -> Boolean) {
    if (!isValid()) return

    updateDocument(Side.RIGHT, VcsBundle.message("command.name.rollback.change")) {
      documentTracker.partiallyApplyBlocks(Side.RIGHT, condition) { block, shift ->
        fireLinesUnchanged(block.start + shift, block.start + shift + (block.vcsEnd - block.vcsStart))
      }
    }
  }


  fun isLineModified(line: Int): Boolean {
    return isRangeModified(line, line + 1)
  }

  fun isRangeModified(startLine: Int, endLine: Int): Boolean {
    if (startLine == endLine) return false
    assert(startLine < endLine)

    LOCK.read {
      if (!isValid()) return false
      for (block in blocks) {
        if (block.start >= endLine) return false
        if (block.end > startLine) return true
      }
      return false
    }
  }

  fun transferLineToFromVcs(line: Int, approximate: Boolean): Int {
    return transferLine(line, approximate, true)
  }

  fun transferLineToVcs(line: Int, approximate: Boolean): Int {
    return transferLine(line, approximate, false)
  }

  private fun transferLine(line: Int, approximate: Boolean, fromVcs: Boolean): Int {
    LOCK.read {
      if (!isValid()) return if (approximate) line else ABSENT_LINE_NUMBER

      var result = line

      for (block in blocks) {
        val startLine1 = if (fromVcs) block.vcsStart else block.start
        val endLine1 = if (fromVcs) block.vcsEnd else block.end
        val startLine2 = if (fromVcs) block.start else block.vcsStart
        val endLine2 = if (fromVcs) block.end else block.vcsEnd

        if (line in startLine1 until endLine1) {
          return if (approximate) startLine2 else ABSENT_LINE_NUMBER
        }

        if (endLine1 > line) return result

        val length1 = endLine1 - startLine1
        val length2 = endLine2 - startLine2
        result += length2 - length1
      }
      return result
    }
  }


  protected open class BlockData(internal var innerRanges: List<Range.InnerRange>? = null,
                                 internal var rangeHighlighter: RangeHighlighter? = null)

  open protected fun createBlockData(): BlockData = BlockData()
  open protected val Block.ourData: BlockData get() = getBlockData(this)
  protected fun getBlockData(block: Block): BlockData {
    if (block.data == null) block.data = createBlockData()
    return block.data as BlockData
  }

  protected val Block.innerRanges: List<Range.InnerRange>? get() = this.ourData.innerRanges.nullize()


  companion object {
    @JvmStatic protected val LOG = Logger.getInstance("#com.intellij.openapi.vcs.ex.LineStatusTracker")

    @JvmStatic protected val Block.start: Int get() = range.start2
    @JvmStatic protected val Block.end: Int get() = range.end2
    @JvmStatic protected val Block.vcsStart: Int get() = range.start1
    @JvmStatic protected val Block.vcsEnd: Int get() = range.end1

    @JvmStatic protected fun Block.isSelectedByLine(line: Int) = DiffUtil.isSelectedByLine(line, this.range.start2, this.range.end2)
    @JvmStatic protected fun Block.isSelectedByLine(lines: BitSet) = DiffUtil.isSelectedByLine(lines, this.range.start2, this.range.end2)
  }


  @TestOnly
  fun getDocumentTrackerInTestMode() = documentTracker
}
