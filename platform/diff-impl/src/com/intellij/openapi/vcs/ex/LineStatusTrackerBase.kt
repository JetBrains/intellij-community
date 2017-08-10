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
import com.intellij.diff.util.DiffUtil.getLineCount
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationAdapter
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.undo.UndoConstants
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.localVcs.UpToDateLineNumberProvider.ABSENT_LINE_NUMBER
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.diff.FilesTooBigForDiffException
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.annotations.CalledWithWriteLock
import java.util.*

abstract class LineStatusTrackerBase {
  // all variables should be modified in EDT and under LOCK
  // read access allowed from EDT or while holding LOCK
  private val LOCK = Any()

  val project: Project?

  val document: Document
  val vcsDocument: Document

  protected abstract val renderer: LineStatusMarkerRenderer

  private val application: Application = ApplicationManager.getApplication()

  private val documentListener: DocumentListener
  private val applicationListener: ApplicationAdapter

  private var isInitialized: Boolean = false
  private var isDuringRollback: Boolean = false
  private var isBulkUpdate: Boolean = false
  private var isAnathemaThrown: Boolean = false
  private var isReleased: Boolean = false

  private var myRanges: List<RangeImpl> = emptyList()

  // operation delayed till the end of write action
  private val toBeDestroyedRanges = ContainerUtil.newIdentityTroveSet<RangeImpl>()
  private val toBeInstalledRanges = ContainerUtil.newIdentityTroveSet<RangeImpl>()

  private var dirtyRange: DirtyRange? = null

  constructor(project: Project?, document: Document) {
    this.project = project
    this.document = document

    documentListener = MyDocumentListener()
    this.document.addDocumentListener(documentListener)

    applicationListener = MyApplicationListener()
    application.addApplicationListener(applicationListener)

    vcsDocument = DocumentImpl("", true)
    vcsDocument.putUserData(UndoConstants.DONT_RECORD_UNDO, java.lang.Boolean.TRUE)
  }

  //
  // Abstract
  //

  @CalledInAwt
  protected open fun isDetectWhitespaceChangedLines(): Boolean = false

  @CalledInAwt
  protected open fun installNotification(text: String) {
  }

  @CalledInAwt
  protected open fun destroyNotification() {
  }

  @CalledInAwt
  protected open fun fireFileUnchanged() {
  }

  open val virtualFile: VirtualFile? = null

  //
  // Impl
  //

  @CalledInAwt
  fun setBaseRevision(vcsContent: CharSequence) {
    application.assertIsDispatchThread()
    if (isReleased) return

    synchronized(LOCK) {
      try {
        vcsDocument.setReadOnly(false)
        vcsDocument.setText(vcsContent)
        vcsDocument.setReadOnly(true)
      }
      finally {
        isInitialized = true
      }

      reinstallRanges()
    }
  }

  @CalledInAwt
  protected fun reinstallRanges() {
    if (!isInitialized || isReleased || isBulkUpdate) return

    synchronized(LOCK) {
      destroyRanges()
      try {
        myRanges = RangesBuilder.createRanges(document, vcsDocument, isDetectWhitespaceChangedLines()).map(::RangeImpl)
        for (range in myRanges) {
          installHighlighter(range)
        }

        if (myRanges.isEmpty()) {
          fireFileUnchanged()
        }
      }
      catch (e: FilesTooBigForDiffException) {
        installAnathema()
      }
    }
  }

  @CalledInAwt
  private fun destroyRanges() {
    removeAnathema()
    for (range in myRanges) {
      disposeHighlighter(range)
    }
    for (range in toBeDestroyedRanges) {
      disposeHighlighter(range)
    }
    myRanges = emptyList()
    toBeDestroyedRanges.clear()
    toBeInstalledRanges.clear()
    dirtyRange = null
  }

  @CalledInAwt
  private fun installAnathema() {
    isAnathemaThrown = true
    installNotification("Can not highlight changed lines. File is too big and there are too many changes.")
  }

  @CalledInAwt
  private fun removeAnathema() {
    if (!isAnathemaThrown) return
    isAnathemaThrown = false
    destroyNotification()
  }

  @CalledInAwt
  private fun installHighlighter(range: RangeImpl) {
    application.assertIsDispatchThread()
    if (range.highlighter != null) {
      LOG.error("Multiple highlighters registered for the same Range")
      return
    }

    try {
      val highlighter = renderer.createHighlighter(range.toRange())
      range.highlighter = highlighter
    }
    catch (e: Exception) {
      LOG.error(e)
    }
  }

  @CalledInAwt
  private fun disposeHighlighter(range: RangeImpl) {
    try {
      val highlighter = range.highlighter
      if (highlighter != null) {
        range.highlighter = null
        highlighter.dispose()
      }
    }
    catch (e: Exception) {
      LOG.error(e)
    }
  }

  private fun tryValidate(): Boolean {
    if (application.isDispatchThread) updateRanges()
    return isValid()
  }

  fun isOperational(): Boolean = synchronized(LOCK) {
    return isInitialized && !isReleased
  }

  fun isValid(): Boolean = synchronized(LOCK) {
    return !isSuppressed() && dirtyRange == null
  }

  private fun isSuppressed(): Boolean {
    return !isInitialized || isReleased || isAnathemaThrown || isBulkUpdate || isDuringRollback
  }

  fun release() {
    val runnable = Runnable {
      if (isReleased) return@Runnable
      LOG.assertTrue(!isDuringRollback)

      synchronized(LOCK) {
        isReleased = true
        document.removeDocumentListener(documentListener)
        application.removeApplicationListener(applicationListener)

        destroyRanges()
      }
    }

    if (application.isDispatchThread && !isDuringRollback) {
      runnable.run()
    }
    else {
      application.invokeLater(runnable)
    }
  }

  /**
   * Ranges can be modified without taking the write lock, so calling this method twice not from EDT can produce different results.
   */
  fun getRanges(): List<Range>? = synchronized(LOCK) {
    if (!tryValidate()) return null
    application.assertReadAccessAllowed()

    val result = ArrayList<Range>(myRanges.size)
    for (range in myRanges) {
      result.add(range.toRange())
    }
    return result
  }

  @CalledInAwt
  fun startBulkUpdate() {
    if (isReleased) return
    synchronized(LOCK) {
      isBulkUpdate = true
      destroyRanges()
    }
  }

  @CalledInAwt
  fun finishBulkUpdate() {
    if (isReleased) return
    synchronized(LOCK) {
      isBulkUpdate = false
      reinstallRanges()
    }
  }

  @CalledInAwt
  private fun updateRanges() {
    if (isSuppressed()) return
    if (dirtyRange != null) {
      synchronized(LOCK) {
        try {
          doUpdateRanges(dirtyRange!!.line1, dirtyRange!!.line2, dirtyRange!!.lineShift, dirtyRange!!.beforeTotalLines)
          dirtyRange = null
        }
        catch (e: Exception) {
          LOG.error(e)
          reinstallRanges()
        }
      }
    }
  }

  @CalledInAwt
  private fun updateRangeHighlighters() {
    if (toBeInstalledRanges.isEmpty && toBeDestroyedRanges.isEmpty) return

    synchronized(LOCK) {
      toBeInstalledRanges.removeAll(toBeDestroyedRanges)

      for (range in toBeDestroyedRanges) {
        disposeHighlighter(range)
      }
      for (range in toBeInstalledRanges) {
        installHighlighter(range)
      }
      toBeDestroyedRanges.clear()
      toBeInstalledRanges.clear()
    }
  }

  private inner class MyApplicationListener : ApplicationAdapter() {
    override fun afterWriteActionFinished(action: Any) {
      updateRanges()
      updateRangeHighlighters()
    }
  }

  private inner class MyDocumentListener : DocumentListener {
    /*
     *   beforeWriteLock   beforeChange     Current
     *              |            |             |
     *              |            | line1       |
     * updatedLine1 +============+-------------+ newLine1
     *              |            |             |
     *      r.line1 +------------+ oldLine1    |
     *              |            |             |
     *              |     old    |             |
     *              |    dirty   |             |
     *              |            | oldLine2    |
     *      r.line2 +------------+         ----+ newLine2
     *              |            |        /    |
     * updatedLine2 +============+--------     |
     *                            line2
     */

    private var myLine1: Int = 0
    private var myLine2: Int = 0
    private var myBeforeTotalLines: Int = 0

    override fun beforeDocumentChange(e: DocumentEvent) {
      if (isSuppressed()) return
      assert(document === e.document)

      myLine1 = document.getLineNumber(e.offset)
      if (e.oldLength == 0) {
        myLine2 = myLine1 + 1
      }
      else {
        myLine2 = document.getLineNumber(e.offset + e.oldLength) + 1
      }

      myBeforeTotalLines = getLineCount(document)
    }

    override fun documentChanged(e: DocumentEvent) {
      application.assertIsDispatchThread()

      if (isSuppressed()) return
      assert(document === e.document)

      synchronized(LOCK) {
        val newLine1 = myLine1
        val newLine2: Int
        if (e.newLength == 0) {
          newLine2 = newLine1 + 1
        }
        else {
          newLine2 = document.getLineNumber(e.offset + e.newLength) + 1
        }

        val linesShift = newLine2 - newLine1 - (myLine2 - myLine1)

        val fixed = fixRanges(e, myLine1, myLine2)
        val line1 = fixed[0]
        val line2 = fixed[1]

        if (dirtyRange == null) {
          dirtyRange = DirtyRange(line1, line2, linesShift, myBeforeTotalLines)
        }
        else {
          val oldLine1 = dirtyRange!!.line1
          val oldLine2 = dirtyRange!!.line2 + dirtyRange!!.lineShift

          val updatedLine1 = dirtyRange!!.line1 - Math.max(oldLine1 - line1, 0)
          val updatedLine2 = dirtyRange!!.line2 + Math.max(line2 - oldLine2, 0)

          dirtyRange = DirtyRange(updatedLine1, updatedLine2, linesShift + dirtyRange!!.lineShift, dirtyRange!!.beforeTotalLines)
        }
      }
    }
  }

  private fun fixRanges(e: DocumentEvent, line1: Int, line2: Int): IntArray {
    val document = document.charsSequence
    val offset = e.offset

    if (e.oldLength == 0 && e.newLength != 0) {
      if (StringUtil.endsWithChar(e.newFragment, '\n') && isNewline(offset - 1, document)) {
        return intArrayOf(line1, line2 - 1)
      }
      if (StringUtil.startsWithChar(e.newFragment, '\n') && isNewline(offset + e.newLength, document)) {
        return intArrayOf(line1 + 1, line2)
      }
    }
    if (e.oldLength != 0 && e.newLength == 0) {
      if (StringUtil.endsWithChar(e.oldFragment, '\n') && isNewline(offset - 1, document)) {
        return intArrayOf(line1, line2 - 1)
      }
      if (StringUtil.startsWithChar(e.oldFragment, '\n') && isNewline(offset + e.newLength, document)) {
        return intArrayOf(line1 + 1, line2)
      }
    }

    return intArrayOf(line1, line2)
  }

  private fun isNewline(offset: Int, sequence: CharSequence): Boolean {
    if (offset < 0) return false
    if (offset >= sequence.length) return false
    return sequence[offset] == '\n'
  }

  private fun doUpdateRanges(beforeChangedLine1: Int,
                             beforeChangedLine2: Int,
                             linesShift: Int,
                             beforeTotalLines: Int) {
    var beforeChangedLine1 = beforeChangedLine1
    var beforeChangedLine2 = beforeChangedLine2
    LOG.assertTrue(!isReleased)

    val rangesBeforeChange = ArrayList<RangeImpl>()
    val rangesAfterChange = ArrayList<RangeImpl>()
    val changedRanges = ArrayList<RangeImpl>()

    sortRanges(beforeChangedLine1, beforeChangedLine2, linesShift, rangesBeforeChange, changedRanges, rangesAfterChange)

    val firstChangedRange = ContainerUtil.getFirstItem(changedRanges)
    val lastChangedRange = ContainerUtil.getLastItem<RangeImpl, List<RangeImpl>>(changedRanges)

    if (firstChangedRange != null && firstChangedRange.line1 < beforeChangedLine1) {
      beforeChangedLine1 = firstChangedRange.line1
    }
    if (lastChangedRange != null && lastChangedRange.line2 > beforeChangedLine2) {
      beforeChangedLine2 = lastChangedRange.line2
    }

    doUpdateRanges(beforeChangedLine1, beforeChangedLine2, linesShift, beforeTotalLines,
                   rangesBeforeChange, changedRanges, rangesAfterChange)
  }

  private fun doUpdateRanges(beforeChangedLine1: Int,
                             beforeChangedLine2: Int,
                             linesShift: Int, // before -> after
                             beforeTotalLines: Int,
                             rangesBefore: List<RangeImpl>,
                             changedRanges: List<RangeImpl>,
                             rangesAfter: List<RangeImpl>) {
    try {
      val vcsTotalLines = getLineCount(vcsDocument)

      val lastRangeBefore = ContainerUtil.getLastItem(rangesBefore)
      val firstRangeAfter = ContainerUtil.getFirstItem(rangesAfter)


      val afterChangedLine1 = beforeChangedLine1
      val afterChangedLine2 = beforeChangedLine2 + linesShift

      val vcsLine1 = getVcsLine1(lastRangeBefore, beforeChangedLine1)
      val vcsLine2 = getVcsLine2(firstRangeAfter, beforeChangedLine2, beforeTotalLines, vcsTotalLines)

      val newChangedRanges = getNewChangedRanges(afterChangedLine1, afterChangedLine2, vcsLine1, vcsLine2)

      shiftRanges(rangesAfter, linesShift)

      if (changedRanges != newChangedRanges) {
        val newRanges = ArrayList<RangeImpl>(rangesBefore.size + newChangedRanges.size + rangesAfter.size)
        newRanges.addAll(rangesBefore)
        newRanges.addAll(newChangedRanges)
        newRanges.addAll(rangesAfter)
        myRanges = newRanges

        toBeDestroyedRanges.addAll(changedRanges)
        toBeInstalledRanges.addAll(newChangedRanges)

        if (myRanges.isEmpty()) {
          fireFileUnchanged()
        }
      }
    }
    catch (ignore: ProcessCanceledException) {
    }
    catch (e: FilesTooBigForDiffException) {
      destroyRanges()
      installAnathema()
    }
  }

  private fun getVcsLine1(range: RangeImpl?, line: Int): Int {
    return if (range == null) line else line + range.vcsLine2 - range.line2
  }

  private fun getVcsLine2(range: RangeImpl?, line: Int, totalLinesBefore: Int, totalLinesAfter: Int): Int {
    return if (range == null) totalLinesAfter - totalLinesBefore + line else line + range.vcsLine1 - range.line1
  }

  @Throws(FilesTooBigForDiffException::class)
  private fun getNewChangedRanges(changedLine1: Int, changedLine2: Int, vcsLine1: Int, vcsLine2: Int): List<RangeImpl> {
    if (changedLine1 == changedLine2 && vcsLine1 == vcsLine2) {
      return emptyList()
    }
    if (changedLine1 == changedLine2) {
      return listOf(RangeImpl(changedLine1, changedLine2, vcsLine1, vcsLine2, null))
    }
    if (vcsLine1 == vcsLine2) {
      return listOf(RangeImpl(changedLine1, changedLine2, vcsLine1, vcsLine2, null))
    }

    val lines = DiffUtil.getLines(document, changedLine1, changedLine2)
    val vcsLines = DiffUtil.getLines(vcsDocument, vcsLine1, vcsLine2)

    return RangesBuilder.createRanges(lines, vcsLines, changedLine1, vcsLine1, isDetectWhitespaceChangedLines()).map(::RangeImpl)
  }

  private fun shiftRanges(rangesAfterChange: List<RangeImpl>, shift: Int) {
    for (range in rangesAfterChange) {
      range.shift(shift)
    }
  }

  private fun sortRanges(beforeChangedLine1: Int,
                         beforeChangedLine2: Int,
                         linesShift: Int,
                         rangesBeforeChange: MutableList<RangeImpl>,
                         changedRanges: MutableList<RangeImpl>,
                         rangesAfterChange: MutableList<RangeImpl>) {
    var lastBefore = -1
    var firstAfter = myRanges.size
    for (i in myRanges.indices) {
      val range = myRanges[i]

      if (range.line2 < beforeChangedLine1) {
        lastBefore = i
      }
      else if (range.line1 > beforeChangedLine2) {
        firstAfter = i
        break
      }
    }

    // Expand on ranges, that are separated from changed lines only by whitespaces

    while (lastBefore != -1) {
      var firstChangedLine = beforeChangedLine1
      if (lastBefore + 1 < myRanges.size) {
        val firstChanged = myRanges[lastBefore + 1]
        firstChangedLine = Math.min(firstChangedLine, firstChanged.line1)
      }

      if (!isLineRangeEmpty(document, myRanges[lastBefore].line2, firstChangedLine)) {
        break
      }

      lastBefore--
    }

    while (firstAfter != myRanges.size) {
      var firstUnchangedLineAfter = beforeChangedLine2 + linesShift
      if (firstAfter > 0) {
        val lastChanged = myRanges[firstAfter - 1]
        firstUnchangedLineAfter = Math.max(firstUnchangedLineAfter, lastChanged.line2 + linesShift)
      }

      if (!isLineRangeEmpty(document, firstUnchangedLineAfter, myRanges[firstAfter].line1 + linesShift)) {
        break
      }

      firstAfter++
    }

    for (i in myRanges.indices) {
      val range = myRanges[i]
      if (i <= lastBefore) {
        rangesBeforeChange.add(range)
      }
      else if (i >= firstAfter) {
        rangesAfterChange.add(range)
      }
      else {
        changedRanges.add(range)
      }
    }
  }

  private fun isLineRangeEmpty(document: Document, line1: Int, line2: Int): Boolean {
    val lineCount = getLineCount(document)
    val startOffset = if (line1 == lineCount) document.textLength else document.getLineStartOffset(line1)
    val endOffset = if (line2 == lineCount) document.textLength else document.getLineStartOffset(line2)

    val interval = document.immutableCharSequence.subSequence(startOffset, endOffset)
    return StringUtil.isEmptyOrSpaces(interval)
  }


  fun findRange(range: Range): Range? {
    synchronized(LOCK) {
      if (!tryValidate()) return null
      return myRanges.find { it.sameAs(range) }?.toRange()
    }
  }

  fun getNextRange(line: Int): Range? {
    synchronized(LOCK) {
      if (!tryValidate()) return null
      for (range in myRanges) {
        if (line < range.line2 && !range.isSelectedByLine(line)) {
          return range.toRange()
        }
      }
      return null
    }
  }

  fun getPrevRange(line: Int): Range? {
    synchronized(LOCK) {
      if (!tryValidate()) return null
      for (i in myRanges.indices.reversed()) {
        val range = myRanges[i]
        if (line > range.line1 && !range.isSelectedByLine(line)) {
          return range.toRange()
        }
      }
      return null
    }
  }

  fun getRangeForLine(line: Int): Range? {
    synchronized(LOCK) {
      if (!tryValidate()) return null
      for (range in myRanges) {
        if (range.isSelectedByLine(line)) return range.toRange()
      }
      return null
    }
  }

  protected open fun doRollbackRange(range: Range) {
    DiffUtil.applyModification(document, range.line1, range.line2, vcsDocument, range.vcsLine1, range.vcsLine2)
  }

  @CalledWithWriteLock
  fun rollbackChanges(range: Range) {
    val rangeImpl = myRanges.find { it.sameAs(range) }
    if (rangeImpl != null) rollbackChanges(listOf(rangeImpl))
  }

  @CalledWithWriteLock
  fun rollbackChanges(lines: BitSet) {
    val toRollback = ArrayList<RangeImpl>()
    for (range in myRanges) {
      val check = DiffUtil.isSelectedByLine(lines, range.line1, range.line2)
      if (check) {
        toRollback.add(range)
      }
    }

    rollbackChanges(toRollback)
  }

  /**
   * @param ranges - sorted list of ranges to rollback
   */
  @CalledWithWriteLock
  private fun rollbackChanges(ranges: List<RangeImpl>) {
    runBulkRollback {
      var first: RangeImpl? = null
      var last: RangeImpl? = null

      var shift = 0
      for (range in ranges) {
        if (first == null) {
          first = range
        }
        last = range

        val shiftedRange = Range(range.line1 + shift, range.line2 + shift,
                                 range.vcsLine1, range.vcsLine2)
        doRollbackRange(shiftedRange)

        shift += range.vcsLine2 - range.vcsLine1 - (range.line2 - range.line1)
      }

      if (first != null) {
        val beforeChangedLine1 = first.line1
        val beforeChangedLine2 = last!!.line2

        val beforeTotalLines = getLineCount(document) - shift

        doUpdateRanges(beforeChangedLine1, beforeChangedLine2, shift, beforeTotalLines)
        updateRangeHighlighters()
      }
    }
  }

  @CalledWithWriteLock
  private fun runBulkRollback(task: () -> Unit) {
    application.assertWriteAccessAllowed()
    if (!tryValidate()) return

    synchronized(LOCK) {
      try {
        isDuringRollback = true

        task()
      }
      catch (e: Error) {
        reinstallRanges()
        throw e
      }
      catch (e: RuntimeException) {
        reinstallRanges()
        throw e
      }
      finally {
        isDuringRollback = false
      }
    }
  }

  fun isLineModified(line: Int): Boolean {
    return isRangeModified(line, line + 1)
  }

  fun isRangeModified(line1: Int, line2: Int): Boolean {
    synchronized(LOCK) {
      if (!tryValidate()) return false
      if (line1 == line2) return false
      assert(line1 < line2)

      for (range in myRanges) {
        if (range.line1 >= line2) return false
        if (range.line2 > line1) return true
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
    synchronized(LOCK) {
      if (!tryValidate()) return if (approximate) line else ABSENT_LINE_NUMBER

      var result = line

      for (range in myRanges) {
        val startLine1 = if (fromVcs) range.vcsLine1 else range.line1
        val endLine1 = if (fromVcs) range.vcsLine2 else range.line2
        val startLine2 = if (fromVcs) range.line1 else range.vcsLine1
        val endLine2 = if (fromVcs) range.line2 else range.vcsLine2

        if (startLine1 <= line && endLine1 > line) {
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

  private class DirtyRange(val line1: Int, val line2: Int, val lineShift: Int, val beforeTotalLines: Int)

  private class RangeImpl(
    var line1: Int,
    var line2: Int,
    var vcsLine1: Int,
    var vcsLine2: Int,

    var innerRanges: List<Range.InnerRange>?,
    var highlighter: RangeHighlighter? = null
  ) {
    constructor(range: Range) : this(range.line1, range.line2, range.vcsLine1, range.vcsLine2, range.innerRanges)

    fun toRange(): Range = Range(line1, line2, vcsLine1, vcsLine2, innerRanges)
    fun sameAs(range: Range) = line1 == range.line1 && line2 == range.line2 &&
                               vcsLine1 == range.vcsLine1 && vcsLine2 == range.vcsLine2

    fun isSelectedByLine(line: Int): Boolean = DiffUtil.isSelectedByLine(line, line1, line2)

    fun shift(shift: Int) {
      line1 += shift
      line2 += shift
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other?.javaClass != javaClass) return false

      other as RangeImpl

      if (line1 != other.line1) return false
      if (line2 != other.line2) return false
      if (vcsLine1 != other.vcsLine1) return false
      if (vcsLine2 != other.vcsLine2) return false
      if (innerRanges != other.innerRanges) return false

      return true
    }

    override fun hashCode(): Int {
      var result = line1
      result = 31 * result + line2
      result = 31 * result + vcsLine1
      result = 31 * result + vcsLine2
      result = 31 * result + (innerRanges?.hashCode() ?: 0)
      return result
    }
  }

  companion object {
    protected val LOG = Logger.getInstance("#com.intellij.openapi.vcs.ex.LineStatusTracker")
  }
}
