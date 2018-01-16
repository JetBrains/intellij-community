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

import com.intellij.diff.comparison.iterables.DiffIterableUtil
import com.intellij.diff.comparison.iterables.FairDiffIterable
import com.intellij.diff.comparison.trimStart
import com.intellij.diff.tools.util.text.LineOffsets
import com.intellij.diff.util.DiffUtil
import com.intellij.diff.util.Range
import com.intellij.diff.util.Side
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationAdapter
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.DocumentBulkUpdateListener
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.ex.DocumentTracker.Block
import com.intellij.openapi.vcs.ex.DocumentTracker.Listener
import com.intellij.util.EventDispatcher
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.CalledInAwt
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class DocumentTracker : Disposable {
  private val dispatcher: EventDispatcher<Listener> = EventDispatcher.create(Listener::class.java)
  private val multicaster = dispatcher.multicaster

  // Any external calls (ex: Document modifications) must be avoided under lock,
  // do avoid deadlock with ChangeListManager
  internal val LOCK: Lock = Lock()

  val document1: Document
  val document2: Document

  private val tracker: LineTracker = LineTracker(dispatcher)
  private val freezeHelper: FreezeHelper = FreezeHelper()

  private var isDisposed: Boolean = false


  constructor(document1: Document, document2: Document) {
    assert(document1 != document2)
    this.document1 = document1
    this.document2 = document2

    val changes = compareLines(document1.immutableCharSequence,
                               document2.immutableCharSequence,
                               document1.lineOffsets,
                               document2.lineOffsets).iterateChanges().toList()
    tracker.setRanges(changes, false)

    val application = ApplicationManager.getApplication()
    application.addApplicationListener(MyApplicationListener(), this)
    application.messageBus.connect(this)
      .subscribe(DocumentBulkUpdateListener.TOPIC, MyDocumentBulkUpdateListener())

    document1.addDocumentListener(MyDocumentListener(Side.LEFT), this)
    document2.addDocumentListener(MyDocumentListener(Side.RIGHT), this)
  }

  @CalledInAwt
  override fun dispose() {
    ApplicationManager.getApplication().assertIsDispatchThread()

    if (isDisposed) return
    isDisposed = true

    LOCK.write {
      tracker.destroy()
    }
  }


  val blocks: List<Block> get() = tracker.blocks

  fun addListener(listener: Listener) {
    dispatcher.addListener(listener)
  }


  fun <T> readLock(task: () -> T): T = LOCK.read(task)
  fun <T> writeLock(task: () -> T): T = LOCK.write(task)
  val isLockHeldByCurrentThread: Boolean get() = LOCK.isHeldByCurrentThread


  fun isFrozen(): Boolean {
    LOCK.read {
      return freezeHelper.isFrozen()
    }
  }

  @CalledInAwt
  fun freeze(side: Side) {
    LOCK.write {
      freezeHelper.freeze(side)
    }
  }

  @CalledInAwt
  fun unfreeze(side: Side) {
    LOCK.write {
      freezeHelper.unfreeze(side)
    }
  }

  @CalledInAwt
  inline fun doFrozen(task: () -> Unit) {
    doFrozen(Side.LEFT) {
      doFrozen(Side.RIGHT) {
        task()
      }
    }
  }

  @CalledInAwt
  inline fun doFrozen(side: Side, task: () -> Unit) {
    freeze(side)
    try {
      task()
    }
    finally {
      unfreeze(side)
    }
  }

  fun getContent(side: Side): CharSequence {
    LOCK.read {
      val frozenContent = freezeHelper.getFrozenContent(side)
      if (frozenContent != null) return frozenContent
      return side[document1, document2].immutableCharSequence
    }
  }


  @CalledInAwt
  fun refreshDirty(fastRefresh: Boolean, forceInFrozen: Boolean = false) {
    if (isDisposed) return
    if (!forceInFrozen && freezeHelper.isFrozen()) return

    LOCK.write {
      if (!blocks.isEmpty() &&
          StringUtil.equals(document1.immutableCharSequence, document2.immutableCharSequence)) {
        tracker.setRanges(emptyList(), false)
        return
      }

      tracker.refreshDirty(document1.immutableCharSequence,
                           document2.immutableCharSequence,
                           document1.lineOffsets,
                           document2.lineOffsets,
                           fastRefresh)
    }
  }

  private fun unfreeze(side: Side, oldText: CharSequence) {
    assert(LOCK.isHeldByCurrentThread)
    if (isDisposed) return

    val newText = side[document1, document2]

    var shift = 0
    val iterable = compareLines(oldText, newText.immutableCharSequence, oldText.lineOffsets, newText.lineOffsets)
    for (range in iterable.changes()) {
      val beforeLength = range.end1 - range.start1
      val afterLength = range.end2 - range.start2
      tracker.rangeChanged(side, range.start1 + shift, beforeLength, afterLength)
      shift += afterLength - beforeLength
    }
  }

  private fun updateFrozenContentIfNeeded() {
    // ensure blocks are up to date
    updateFrozenContentIfNeeded(Side.LEFT)
    updateFrozenContentIfNeeded(Side.RIGHT)
    refreshDirty(fastRefresh = false, forceInFrozen = true)
  }

  private fun updateFrozenContentIfNeeded(side: Side) {
    assert(LOCK.isHeldByCurrentThread)
    if (!freezeHelper.isFrozen(side)) return

    unfreeze(side, freezeHelper.getFrozenContent(side)!!)

    freezeHelper.setFrozenContent(side, side[document1, document2].immutableCharSequence)
  }


  @CalledInAwt
  fun partiallyApplyBlocks(side: Side, condition: (Block) -> Boolean, consumer: (Block, shift: Int) -> Unit) {
    if (isDisposed) return

    val otherSide = side.other()
    val document = side[document1, document2]
    val otherDocument = otherSide[document1, document2]

    doFrozen(side) {
      val appliedBlocks = LOCK.write {
        updateFrozenContentIfNeeded()
        tracker.partiallyApplyBlocks(side, condition)
      }

      // We use already filtered blocks here, because conditions might have been changed from other thread.
      // The documents/blocks themselves did not change though.
      LineTracker.processAppliedRanges(appliedBlocks, { true }, side) { block, shift, _ ->
        DiffUtil.applyModification(document, block.range.start(side) + shift, block.range.end(side) + shift,
                                   otherDocument, block.range.start(otherSide), block.range.end(otherSide))

        consumer(block, shift)
      }

      LOCK.write {
        freezeHelper.setFrozenContent(side, document.immutableCharSequence)
      }
    }
  }

  fun getContentWithPartiallyAppliedBlocks(side: Side, condition: (Block) -> Boolean): String {
    val otherSide = side.other()
    val affectedBlocks = LOCK.write {
      updateFrozenContentIfNeeded()
      tracker.blocks.filter(condition)
    }

    val content = getContent(side)
    val otherContent = getContent(otherSide)

    val lineOffsets = content.lineOffsets
    val otherLineOffsets = otherContent.lineOffsets

    val ranges = affectedBlocks.map {
      Range(it.range.start(side), it.range.end(side),
            it.range.start(otherSide), it.range.end(otherSide))
    }

    return DiffUtil.applyModification(content, lineOffsets, otherContent, otherLineOffsets, ranges)
  }

  fun setFrozenState(content1: CharSequence, content2: CharSequence, lineRanges: List<Range>): Boolean {
    assert(freezeHelper.isFrozen(Side.LEFT) && freezeHelper.isFrozen(Side.RIGHT))
    if (isDisposed) return false

    LOCK.write {
      if (!isValidState(content1, content2, lineRanges)) return false

      freezeHelper.setFrozenContent(Side.LEFT, content1)
      freezeHelper.setFrozenContent(Side.RIGHT, content2)
      tracker.setRanges(lineRanges, true)

      return true
    }
  }

  @CalledInAwt
  fun setFrozenState(lineRanges: List<Range>): Boolean {
    if (isDisposed) return false
    assert(freezeHelper.isFrozen(Side.LEFT) && freezeHelper.isFrozen(Side.RIGHT))

    LOCK.write {
      val content1 = getContent(Side.LEFT)
      val content2 = getContent(Side.RIGHT)
      if (!isValidState(content1, content2, lineRanges)) return false

      tracker.setRanges(lineRanges, true)

      return true
    }
  }

  private fun isValidState(content1: CharSequence,
                           content2: CharSequence,
                           lineRanges: List<Range>): Boolean {
    val lineOffset1 = content1.lineOffsets
    val lineOffset2 = content2.lineOffsets

    val iterable = DiffIterableUtil.create(lineRanges, lineOffset1.lineCount, lineOffset2.lineCount)
    for (range in iterable.unchanged()) {
      val lines1 = DiffUtil.getLines(content1, lineOffset1, range.start1, range.end1)
      val lines2 = DiffUtil.getLines(content2, lineOffset2, range.start2, range.end2)
      if (lines1 != lines2) return false
    }
    return true
  }



  private inner class MyDocumentBulkUpdateListener : DocumentBulkUpdateListener {
    init {
      if ((document1 as DocumentEx).isInBulkUpdate) freeze(Side.LEFT)
      if ((document2 as DocumentEx).isInBulkUpdate) freeze(Side.RIGHT)
    }

    override fun updateStarted(doc: Document) {
      if (document1 == doc) freeze(Side.LEFT)
      if (document2 == doc) freeze(Side.RIGHT)
    }

    override fun updateFinished(doc: Document) {
      if (document1 == doc) unfreeze(Side.LEFT)
      if (document2 == doc) unfreeze(Side.RIGHT)
    }
  }

  private inner class MyApplicationListener : ApplicationAdapter() {
    override fun afterWriteActionFinished(action: Any) {
      refreshDirty(fastRefresh = true)
    }
  }

  private inner class MyDocumentListener(val side: Side) : DocumentListener {
    private val document = side[document1, document2]

    private var line1: Int = 0
    private var line2: Int = 0

    override fun beforeDocumentChange(e: DocumentEvent) {
      if (isDisposed || freezeHelper.isFrozen(side)) return

      line1 = document.getLineNumber(e.offset)
      if (e.oldLength == 0) {
        line2 = line1 + 1
      }
      else {
        line2 = document.getLineNumber(e.offset + e.oldLength) + 1
      }
    }

    override fun documentChanged(e: DocumentEvent) {
      if (isDisposed || freezeHelper.isFrozen(side)) return

      val newLine2: Int
      if (e.newLength == 0) {
        newLine2 = line1 + 1
      }
      else {
        newLine2 = document.getLineNumber(e.offset + e.newLength) + 1
      }

      val (startLine, afterLength, beforeLength) = getAffectedRange(line1, line2, newLine2, e)

      LOCK.write {
        tracker.rangeChanged(side, startLine, beforeLength, afterLength)
      }
    }

    private fun getAffectedRange(line1: Int, oldLine2: Int, newLine2: Int, e: DocumentEvent): Triple<Int, Int, Int> {
      val afterLength = newLine2 - line1
      val beforeLength = line2 - line1

      // Whole line insertion / deletion
      if (e.oldLength == 0 && e.newLength != 0) {
        if (StringUtil.endsWithChar(e.newFragment, '\n') && isNewlineBefore(e)) {
          return Triple(line1, afterLength - 1, beforeLength - 1)
        }
        if (StringUtil.startsWithChar(e.newFragment, '\n') && isNewlineAfter(e)) {
          return Triple(line1 + 1, afterLength - 1, beforeLength - 1)
        }
      }
      if (e.oldLength != 0 && e.newLength == 0) {
        if (StringUtil.endsWithChar(e.oldFragment, '\n') && isNewlineBefore(e)) {
          return Triple(line1, afterLength - 1, beforeLength - 1)
        }
        if (StringUtil.startsWithChar(e.oldFragment, '\n') && isNewlineAfter(e)) {
          return Triple(line1 + 1, afterLength - 1, beforeLength - 1)
        }
      }

      return Triple(line1, afterLength, beforeLength)
    }

    private fun isNewlineBefore(e: DocumentEvent): Boolean {
      if (e.offset == 0) return true
      return e.document.immutableCharSequence[e.offset - 1] == '\n'
    }

    private fun isNewlineAfter(e: DocumentEvent): Boolean {
      if (e.offset + e.newLength == e.document.immutableCharSequence.length) return true
      return e.document.immutableCharSequence[e.offset + e.newLength] == '\n'
    }
  }


  private inner class FreezeHelper {
    private var data1: FreezeData? = null
    private var data2: FreezeData? = null

    fun isFrozen(side: Side) = getData(side) != null
    fun isFrozen() = isFrozen(Side.LEFT) || isFrozen(Side.RIGHT)

    fun freeze(side: Side) {
      var data = getData(side)
      if (data == null) {
        data = FreezeData(side[document1, document2])
        setData(side, data)
        data.counter++

        multicaster.onFreeze(side)
      }
      else {
        data.counter++
      }
    }

    fun unfreeze(side: Side) {
      val data = getData(side)
      if (data == null || data.counter == 0) {
        LOG.error("DocumentTracker is not freezed: $side, ${data1?.counter ?: -1}, ${data2?.counter ?: -1}")
        return
      }

      data.counter--

      if (data.counter == 0) {
        unfreeze(side, data.textBeforeFreeze)

        setData(side, null)
        refreshDirty(fastRefresh = false)
        multicaster.onUnfreeze(side)
      }
    }

    private fun getData(side: Side) = side[data1, data2]
    private fun setData(side: Side, data: FreezeData?) {
      if (side.isLeft) {
        data1 = data
      }
      else {
        data2 = data
      }
    }

    fun getFrozenContent(side: Side): CharSequence? = getData(side)?.textBeforeFreeze
    fun setFrozenContent(side: Side, newContent: CharSequence) {
      setData(side, FreezeData(getData(side)!!, newContent))
    }
  }

  private class FreezeData(val textBeforeFreeze: CharSequence, var counter: Int) {
    constructor(document: Document) : this(document.immutableCharSequence, 0)
    constructor(data: FreezeData, textBeforeFreeze: CharSequence) : this(textBeforeFreeze, data.counter)
  }


  internal inner class Lock {
    private val myLock = ReentrantLock()

    internal inline fun <T> read(task: () -> T): T {
      return myLock.withLock(task)
    }

    internal inline fun <T> write(task: () -> T): T {
      return myLock.withLock(task)
    }

    internal val isHeldByCurrentThread: Boolean
      get() = myLock.isHeldByCurrentThread
  }

  interface Listener : EventListener {
    fun onRangeRefreshed(before: Block, after: List<Block>) {}
    fun onRangesChanged(before: List<Block>, after: Block) {}
    fun onRangeShifted(before: Block, after: Block) {}

    fun onRangesMerged(range1: Block, range2: Block, merged: Block): Boolean = true

    fun onRangeRemoved(block: Block) {}
    fun onRangeAdded(block: Block) {}

    fun afterRefresh() {}
    fun afterRangeChange() {}
    fun afterExplicitChange() {}

    fun onFreeze(side: Side) {}
    fun onUnfreeze(side: Side) {}
  }


  class Block(val range: Range, internal val isDirty: Boolean, internal val isTooBig: Boolean) {
    var data: Any? = null
  }

  companion object {
    private val LOG = Logger.getInstance(DocumentTracker::class.java)
  }
}


private class LineTracker(private val dispatcher: EventDispatcher<Listener>) {
  private val multicaster = dispatcher.multicaster

  var blocks: List<Block> = emptyList()
    private set

  private var isDirty: Boolean = false


  fun destroy() {
    multicaster.onRangesRemoved(blocks)
    blocks = emptyList()
  }

  fun refreshDirty(text1: CharSequence,
                   text2: CharSequence,
                   lineOffsets1: LineOffsets,
                   lineOffsets2: LineOffsets,
                   fastRefresh: Boolean) {
    if (!isDirty) return

    val removedBlocks = ArrayList<Block>()
    val addedBlocks = ArrayList<Block>()

    val newBlocks = ArrayList<Block>()

    BlockGroupsProcessor(text1, lineOffsets1).processMergeableGroups(blocks) { group ->
      if (group.any { it.isDirty }) {
        MergingBlockProcessor(dispatcher).processMergedBlocks(group) { original, mergedBlock ->
          val freshBlocks = refreshBlock(mergedBlock, text1, text2, lineOffsets1, lineOffsets2, fastRefresh)

          removedBlocks.addAll(original)
          addedBlocks.addAll(freshBlocks)
          multicaster.onRangeRefreshed(mergedBlock, freshBlocks)

          newBlocks.addAll(freshBlocks)
        }
      }
      else {
        newBlocks.addAll(group)
      }
    }

    multicaster.onRangesRemoved(removedBlocks)
    multicaster.onRangesAdded(addedBlocks)

    blocks = newBlocks
    isDirty = false

    multicaster.afterRefresh()
  }

  fun rangeChanged(side: Side, startLine: Int, beforeLength: Int, afterLength: Int) {
    val data = handleRangeChange(blocks, side, startLine, beforeLength, afterLength)

    multicaster.onRangesChanged(data.affectedBlocks, data.newAffectedBlock)
    for (i in data.afterBlocks.indices) {
      multicaster.onRangeShifted(data.afterBlocks[i], data.newAfterBlocks[i])
    }

    multicaster.onRangesRemoved(data.affectedBlocks)
    multicaster.onRangesRemoved(data.afterBlocks)

    multicaster.onRangeAdded(data.newAffectedBlock)
    multicaster.onRangesAdded(data.newAfterBlocks)

    blocks = ContainerUtil.concat(data.beforeBlocks, listOf(data.newAffectedBlock), data.newAfterBlocks)
    isDirty = true

    multicaster.afterRangeChange()
  }

  private fun refreshBlock(block: Block,
                           text1: CharSequence,
                           text2: CharSequence,
                           lineOffsets1: LineOffsets,
                           lineOffsets2: LineOffsets,
                           fastRefresh: Boolean): List<Block> {
    if (block.range.isEmpty) return emptyList()

    val iterable: FairDiffIterable
    val isTooBig: Boolean
    if (block.isTooBig && fastRefresh) {
      iterable = fastCompareLines(block.range, text1, text2, lineOffsets1, lineOffsets2)
      isTooBig = true
    }
    else {
      val realIterable = tryCompareLines(block.range, text1, text2, lineOffsets1, lineOffsets2)
      if (realIterable != null) {
        iterable = realIterable
        isTooBig = false
      }
      else {
        iterable = fastCompareLines(block.range, text1, text2, lineOffsets1, lineOffsets2)
        isTooBig = true
      }
    }

    return iterable.iterateChanges().map {
      Block(shiftRange(it, block.range.start1, block.range.start2), false, isTooBig)
    }
  }


  fun partiallyApplyBlocks(side: Side, condition: (Block) -> Boolean): List<Block> {
    val oldBlocks = blocks
    val newBlocks = mutableListOf<Block>()
    val appliedBlocks = mutableListOf<Block>()

    processAppliedRanges(blocks, condition, side) { block, shift, isApplied ->
      if (isApplied) {
        appliedBlocks += block
      }
      else {
        val newBlock = block.shift(side, shift)
        multicaster.onRangeShifted(block, newBlock)

        newBlocks.add(newBlock)
      }
    }

    multicaster.onRangesRemoved(oldBlocks)
    multicaster.onRangesAdded(newBlocks)

    blocks = newBlocks

    multicaster.afterExplicitChange()

    return appliedBlocks
  }

  fun setRanges(ranges: List<Range>, dirty: Boolean) {
    val oldBlocks = blocks
    val newBlocks = ranges.map { Block(it, dirty, false) }

    multicaster.onRangesRemoved(oldBlocks)
    multicaster.onRangesAdded(newBlocks)

    blocks = newBlocks
    if (dirty) isDirty = true

    multicaster.afterExplicitChange()
  }

  companion object {
    private fun handleRangeChange(blocks: List<Block>,
                                  side: Side,
                                  startLine: Int,
                                  beforeLength: Int,
                                  afterLength: Int): BlockChangeData {
      val endLine = startLine + beforeLength
      val rangeSizeDelta = afterLength - beforeLength

      val (beforeBlocks, affectedBlocks, afterBlocks) = sortRanges(blocks, side, startLine, endLine)

      val ourToOtherShift: Int = getOurToOtherShift(side, beforeBlocks)

      val newAffectedBlock = getNewAffectedBlock(side, startLine, endLine, rangeSizeDelta, ourToOtherShift,
                                                 affectedBlocks)
      val newAfterBlocks = afterBlocks.map { it.shift(side, rangeSizeDelta) }

      return BlockChangeData(beforeBlocks,
                             affectedBlocks, afterBlocks,
                             newAffectedBlock, newAfterBlocks)
    }

    private fun sortRanges(blocks: List<Block>,
                           side: Side,
                           line1: Int,
                           line2: Int): Triple<List<Block>, List<Block>, List<Block>> {
      val beforeChange = ArrayList<Block>()
      val affected = ArrayList<Block>()
      val afterChange = ArrayList<Block>()

      for (block in blocks) {
        if (block.range.end(side) < line1) {
          beforeChange.add(block)
        }
        else if (block.range.start(side) > line2) {
          afterChange.add(block)
        }
        else {
          affected.add(block)
        }
      }

      return Triple(beforeChange, affected, afterChange)
    }

    private fun getOurToOtherShift(side: Side, beforeBlocks: List<Block>): Int {
      val lastBefore = beforeBlocks.lastOrNull()?.range
      val otherShift: Int
      if (lastBefore == null) {
        otherShift = 0
      }
      else {
        otherShift = lastBefore.end(side.other()) - lastBefore.end(side)
      }
      return otherShift
    }

    private fun getNewAffectedBlock(side: Side,
                                    startLine: Int,
                                    endLine: Int,
                                    rangeSizeDelta: Int,
                                    ourToOtherShift: Int,
                                    affectedBlocks: List<Block>): Block {
      val rangeStart: Int
      val rangeEnd: Int
      val rangeStartOther: Int
      val rangeEndOther: Int

      if (affectedBlocks.isEmpty()) {
        rangeStart = startLine
        rangeEnd = endLine + rangeSizeDelta
        rangeStartOther = startLine + ourToOtherShift
        rangeEndOther = endLine + ourToOtherShift
      }
      else {
        val firstAffected = affectedBlocks.first().range
        val lastAffected = affectedBlocks.last().range

        val affectedStart = firstAffected.start(side)
        val affectedStartOther = firstAffected.start(side.other())
        val affectedEnd = lastAffected.end(side)
        val affectedEndOther = lastAffected.end(side.other())

        if (affectedStart <= startLine) {
          rangeStart = affectedStart
          rangeStartOther = affectedStartOther
        }
        else {
          rangeStart = startLine
          rangeStartOther = startLine + (affectedStartOther - affectedStart)
        }

        if (affectedEnd >= endLine) {
          rangeEnd = affectedEnd + rangeSizeDelta
          rangeEndOther = affectedEndOther
        }
        else {
          rangeEnd = endLine + rangeSizeDelta
          rangeEndOther = endLine + (affectedEndOther - affectedEnd)
        }
      }

      val isTooBig = affectedBlocks.any { it.isTooBig }
      val range = createRange(side, rangeStart, rangeEnd, rangeStartOther, rangeEndOther)
      return Block(range, true, isTooBig)
    }

    fun processAppliedRanges(blocks: List<Block>, condition: (Block) -> Boolean, side: Side,
                             handler: (block: Block, shift: Int, isApplied: Boolean) -> Unit) {
      val otherSide = side.other()

      var shift = 0
      for (block in blocks) {
        if (condition(block)) {
          handler(block, shift, true)

          val deleted = block.range.end(side) - block.range.start(side)
          val inserted = block.range.end(otherSide) - block.range.start(otherSide)
          shift += inserted - deleted
        }
        else {
          handler(block, shift, false)
        }
      }
    }

    private fun Block.shift(side: Side, delta: Int) = Block(
      shiftRange(this.range, side, delta), this.isDirty, this.isTooBig)

    private fun createRange(side: Side, start: Int, end: Int, otherStart: Int, otherEnd: Int): Range {
      return Range(side[start, otherStart], side[end, otherEnd],
                   side[otherStart, start], side[otherEnd, end])
    }

    private fun shiftRange(range: Range, side: Side, shift: Int) = shiftRange(
      range, side[shift, 0], side[0, shift])

    private fun shiftRange(range: Range, shift1: Int, shift2: Int) = Range(range.start1 + shift1,
                                                                           range.end1 + shift1,
                                                                           range.start2 + shift2,
                                                                           range.end2 + shift2)
  }

  private data class BlockChangeData(val beforeBlocks: List<Block>,
                                     val affectedBlocks: List<Block>, val afterBlocks: List<Block>,
                                     val newAffectedBlock: Block, val newAfterBlocks: List<Block>)

  private fun Listener.onRangesRemoved(blocks: List<Block>) {
    blocks.forEach(this::onRangeRemoved)
  }

  private fun Listener.onRangesAdded(blocks: List<Block>) {
    blocks.forEach(this::onRangeAdded)
  }
}

private class BlockGroupsProcessor(private val text1: CharSequence,
                                   private val lineOffsets1: LineOffsets) {
  fun processMergeableGroups(blocks: List<Block>,
                             processGroup: (group: List<Block>) -> Unit) {
    if (blocks.isEmpty()) return

    var i = 0
    var blockStart = 0
    while (i < blocks.size - 1) {
      if (!isWhitespaceOnlySeparated(blocks[i], blocks[i + 1])) {
        processGroup(blocks.subList(blockStart, i + 1))
        blockStart = i + 1
      }
      i += 1
    }
    processGroup(blocks.subList(blockStart, i + 1))
  }

  private fun isWhitespaceOnlySeparated(block1: Block, block2: Block): Boolean {
    val range1 = DiffUtil.getLinesRange(lineOffsets1, block1.range.start1, block1.range.end1, false)
    val range2 = DiffUtil.getLinesRange(lineOffsets1, block2.range.start1, block2.range.end1, false)
    val start = range1.endOffset
    val end = range2.startOffset
    return trimStart(text1, start, end) == end
  }
}

private class MergingBlockProcessor(private val dispatcher: EventDispatcher<Listener>) {
  fun processMergedBlocks(group: List<Block>,
                          processBlock: (original: List<Block>, merged: Block) -> Unit) {
    assert(!group.isEmpty())

    val originalGroup = mutableListOf<Block>()
    var merged: Block? = null

    for (block in group) {
      if (merged == null) {
        originalGroup.add(block)
        merged = block
      }
      else {
        val newMerged = mergeBlocks(merged, block)
        if (newMerged != null) {
          originalGroup.add(block)
          merged = newMerged
        }
        else {
          processBlock(originalGroup, merged)
          originalGroup.clear()
          originalGroup.add(block)
          merged = block
        }
      }
    }

    processBlock(originalGroup, merged!!)
  }

  private fun mergeBlocks(block1: Block, block2: Block): Block? {
    val isDirty = block1.isDirty || block2.isDirty
    val isTooBig = block1.isTooBig || block2.isTooBig
    val range = Range(block1.range.start1, block2.range.end1,
                      block1.range.start2, block2.range.end2)
    val merged = Block(range, isDirty, isTooBig)

    for (listener in dispatcher.listeners) {
      if (!listener.onRangesMerged(block1, block2, merged)) {
        return null // merging vetoed
      }
    }
    return merged
  }
}