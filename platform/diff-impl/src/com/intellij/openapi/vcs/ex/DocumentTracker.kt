// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.ex

import com.intellij.diff.comparison.iterables.DiffIterable
import com.intellij.diff.comparison.iterables.FairDiffIterable
import com.intellij.diff.comparison.trimStart
import com.intellij.diff.tools.util.text.LineOffsets
import com.intellij.diff.util.DiffRangeUtil
import com.intellij.diff.util.DiffUtil
import com.intellij.diff.util.Range
import com.intellij.diff.util.Side
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.ex.DocumentTracker.Block
import com.intellij.openapi.vcs.ex.DocumentTracker.Handler
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.PeekableIteratorWrapper
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.max

/**
 * Any external calls (ex: Document modifications) must be avoided under [LOCK],
 * to avoid deadlocks with application Read/Write action and ChangeListManager.
 *
 * Tracker assumes that both documents are modified on EDT only.
 *
 * Blocks are modified on EDT and under [LOCK].
 */
class DocumentTracker(
  document1: Document,
  document2: Document,
  private val LOCK: Lock
) : Disposable {

  private val handlers: MutableList<Handler> = mutableListOf()

  var document1: Document = document1
    private set
  var document2: Document = document2
    private set

  private val tracker: LineTracker
  private val freezeHelper: FreezeHelper = FreezeHelper()

  private var isDisposed: Boolean = false

  private val documentListener1 = MyDocumentListener(Side.LEFT, document1)
  private val documentListener2 = MyDocumentListener(Side.RIGHT, document2)

  init {
    assert(document1 != document2)

    val changes = when {
      document1.immutableCharSequence === document2.immutableCharSequence -> emptyList()
      else -> compareLines(document1.immutableCharSequence,
                           document2.immutableCharSequence,
                           document1.lineOffsets,
                           document2.lineOffsets).iterateChanges().toList()
    }
    tracker = LineTracker(handlers, changes)

    val application = ApplicationManager.getApplication()
    application.addApplicationListener(MyApplicationListener(), this)
  }

  @RequiresEdt
  override fun dispose() {
    ApplicationManager.getApplication().assertIsDispatchThread()

    if (isDisposed) return
    isDisposed = true

    LOCK.write {
      tracker.destroy()
    }
  }

  @RequiresEdt
  fun addHandler(newHandler: Handler) {
    handlers.add(newHandler)
  }


  val blocks: List<Block> get() = tracker.blocks


  fun <T> readLock(task: () -> T): T = LOCK.read(task)
  fun <T> writeLock(task: () -> T): T = LOCK.write(task)
  val isLockHeldByCurrentThread: Boolean get() = LOCK.isHeldByCurrentThread


  fun isFrozen(): Boolean {
    LOCK.read {
      return freezeHelper.isFrozen()
    }
  }

  fun freeze(side: Side) {
    LOCK.write {
      freezeHelper.freeze(side)
    }
  }

  @RequiresEdt
  fun unfreeze(side: Side) {
    LOCK.write {
      freezeHelper.unfreeze(side)
    }
  }

  @RequiresEdt
  inline fun doFrozen(task: () -> Unit) {
    doFrozen(Side.LEFT) {
      doFrozen(Side.RIGHT) {
        task()
      }
    }
  }

  @RequiresEdt
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

  @RequiresEdt
  fun replaceDocument(side: Side, newDocument: Document) {
    assert(!LOCK.isHeldByCurrentThread)

    doFrozen {
      if (side.isLeft) {
        documentListener1.switchDocument(newDocument)
        document1 = newDocument
      }
      else {
        documentListener2.switchDocument(newDocument)
        document2 = newDocument
      }
    }
  }


  @RequiresEdt
  fun refreshDirty(fastRefresh: Boolean, forceInFrozen: Boolean = false) {
    if (isDisposed) return
    if (!forceInFrozen && freezeHelper.isFrozen()) return

    LOCK.write {
      if (tracker.isDirty &&
          blocks.isNotEmpty() &&
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

    val iterable = compareLines(oldText, newText.immutableCharSequence, oldText.lineOffsets, newText.lineOffsets)
    if (iterable.changes().hasNext()) {
      tracker.rangesChanged(side, iterable)
    }
  }

  @RequiresEdt
  fun updateFrozenContentIfNeeded() {
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


  @RequiresEdt
  fun partiallyApplyBlocks(side: Side, condition: (Block) -> Boolean) {
    partiallyApplyBlocks(side, condition, { _, _ -> })
  }

  @RequiresEdt
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
      var shift = 0
      for (block in appliedBlocks) {
        DiffUtil.applyModification(document, block.range.start(side) + shift, block.range.end(side) + shift,
                                   otherDocument, block.range.start(otherSide), block.range.end(otherSide))

        consumer(block, shift)

        shift += getRangeDelta(block.range, side)
      }

      LOCK.write {
        freezeHelper.setFrozenContent(side, document.immutableCharSequence)
      }
    }
  }

  @RequiresEdt
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
      if (!isValidRanges(content1, content2, content1.lineOffsets, content2.lineOffsets, lineRanges)) return false

      freezeHelper.setFrozenContent(Side.LEFT, content1)
      freezeHelper.setFrozenContent(Side.RIGHT, content2)
      tracker.setRanges(lineRanges, true)

      return true
    }
  }

  @RequiresEdt
  fun setFrozenState(lineRanges: List<Range>): Boolean {
    if (isDisposed) return false
    assert(freezeHelper.isFrozen(Side.LEFT) && freezeHelper.isFrozen(Side.RIGHT))

    LOCK.write {
      val content1 = getContent(Side.LEFT)
      val content2 = getContent(Side.RIGHT)
      if (!isValidRanges(content1, content2, content1.lineOffsets, content2.lineOffsets, lineRanges)) return false

      tracker.setRanges(lineRanges, true)

      return true
    }
  }

  private inner class MyApplicationListener : ApplicationListener {
    override fun afterWriteActionFinished(action: Any) {
      refreshDirty(fastRefresh = true)
    }
  }

  private inner class MyDocumentListener(val side: Side, private var document: Document) : DocumentListener {
    private var line1: Int = 0
    private var line2: Int = 0

    init {
      document.addDocumentListener(this, this@DocumentTracker)
      if (document.isInBulkUpdate) freeze(side)
    }

    fun switchDocument(newDocument: Document) {
      document.removeDocumentListener(this)
      if (document.isInBulkUpdate == true) unfreeze(side)

      document = newDocument
      newDocument.addDocumentListener(this, this@DocumentTracker)
      if (newDocument.isInBulkUpdate) freeze(side)
    }

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

    override fun bulkUpdateStarting(document: Document) {
      freeze(side)
    }

    override fun bulkUpdateFinished(document: Document) {
      unfreeze(side)
    }

    private fun getAffectedRange(line1: Int, oldLine2: Int, newLine2: Int, e: DocumentEvent): Triple<Int, Int, Int> {
      val afterLength = newLine2 - line1
      val beforeLength = oldLine2 - line1

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
      val wasFrozen = isFrozen()

      var data = getData(side)
      if (data == null) {
        data = FreezeData(side[document1, document2])
        setData(side, data)
        data.counter++

        if (wasFrozen) onFreeze()
        onFreeze(side)
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
        onUnfreeze(side)
        if (!isFrozen()) onUnfreeze()
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


    private fun onFreeze(side: Side) {
      handlers.forEach { it.onFreeze(side) }
    }

    private fun onUnfreeze(side: Side) {
      handlers.forEach { it.onUnfreeze(side) }
    }

    private fun onFreeze() {
      handlers.forEach { it.onFreeze() }
    }

    private fun onUnfreeze() {
      handlers.forEach { it.onUnfreeze() }
    }
  }

  private class FreezeData(val textBeforeFreeze: CharSequence, var counter: Int) {
    constructor(document: Document) : this(document.immutableCharSequence, 0)
    constructor(data: FreezeData, textBeforeFreeze: CharSequence) : this(textBeforeFreeze, data.counter)
  }


  @ApiStatus.Internal
  class Lock {
    val myLock = ReentrantLock()

    inline fun <T> read(task: () -> T): T {
      return myLock.withLock(task)
    }

    inline fun <T> write(task: () -> T): T {
      return myLock.withLock(task)
    }

    val isHeldByCurrentThread: Boolean
      get() = myLock.isHeldByCurrentThread
  }

  /**
   * All methods are invoked under [LOCK].
   */
  interface Handler {
    fun onRangeRefreshed(before: Block, after: List<Block>) {}
    fun onRangesChanged(before: List<Block>, after: Block) {}
    fun onRangeShifted(before: Block, after: Block) {}

    /**
     * In some cases, we might want to refresh multiple adjustent blocks together.
     * This method allows to veto such merging (ex: if blocks share conflicting sets of flags).
     *
     * @return true if blocks are allowed to be merged
     */
    fun mergeRanges(block1: Block, block2: Block, merged: Block): Boolean = true

    fun afterBulkRangeChange(isDirty: Boolean) {}

    fun onFreeze(side: Side) {}
    fun onUnfreeze(side: Side) {}

    fun onFreeze() {}
    fun onUnfreeze() {}
  }


  class Block(val range: Range, internal val isDirty: Boolean, internal val isTooBig: Boolean) : BlockI {
    var data: Any? = null

    override val start: Int get() = range.start2
    override val end: Int get() = range.end2
    override val vcsStart: Int get() = range.start1
    override val vcsEnd: Int get() = range.end1
  }

  companion object {
    private val LOG = Logger.getInstance(DocumentTracker::class.java)
  }
}


private class LineTracker(private val handlers: List<Handler>,
                          originalChanges: List<Range>) {
  var blocks: List<Block> = originalChanges.map { Block(it, false, false) }
    private set

  var isDirty: Boolean = false
    private set
  private var forceMergeNearbyBlocks: Boolean = false


  fun setRanges(ranges: List<Range>, dirty: Boolean) {
    val newBlocks = ranges.map { Block(it, dirty, false) }
    for (block in newBlocks) {
      onRangesChanged(emptyList(), block)
    }

    blocks = newBlocks
    isDirty = dirty
    forceMergeNearbyBlocks = false

    afterBulkRangeChange(isDirty)
  }

  fun destroy() {
    blocks = emptyList()
  }

  fun refreshDirty(text1: CharSequence,
                   text2: CharSequence,
                   lineOffsets1: LineOffsets,
                   lineOffsets2: LineOffsets,
                   fastRefresh: Boolean) {
    if (!isDirty) return

    val result = BlocksRefresher(handlers, text1, text2, lineOffsets1, lineOffsets2, forceMergeNearbyBlocks).refresh(blocks, fastRefresh)

    blocks = result.newBlocks
    isDirty = false
    forceMergeNearbyBlocks = false

    afterBulkRangeChange(isDirty)
  }

  fun rangeChanged(side: Side, startLine: Int, beforeLength: Int, afterLength: Int) {
    val data = RangeChangeHandler().run(blocks, side, startLine, beforeLength, afterLength)

    onRangesChanged(data.affectedBlocks, data.newAffectedBlock)
    for (i in data.afterBlocks.indices) {
      onRangeShifted(data.afterBlocks[i], data.newAfterBlocks[i])
    }

    blocks = data.newBlocks
    isDirty = data.newBlocks.isNotEmpty()

    afterBulkRangeChange(isDirty)
  }

  fun rangesChanged(side: Side, iterable: DiffIterable) {
    val newBlocks = BulkRangeChangeHandler(handlers, blocks, side).run(iterable)

    blocks = newBlocks
    isDirty = newBlocks.isNotEmpty()
    forceMergeNearbyBlocks = isDirty

    afterBulkRangeChange(isDirty)
  }

  fun partiallyApplyBlocks(side: Side, condition: (Block) -> Boolean): List<Block> {
    val newBlocks = mutableListOf<Block>()
    val appliedBlocks = mutableListOf<Block>()

    var shift = 0
    for (block in blocks) {
      if (condition(block)) {
        appliedBlocks.add(block)

        shift += getRangeDelta(block.range, side)
      }
      else {
        val newBlock = block.shift(side, shift)
        onRangeShifted(block, newBlock)

        newBlocks.add(newBlock)
      }
    }

    blocks = newBlocks

    afterBulkRangeChange(isDirty)

    return appliedBlocks
  }


  private fun onRangesChanged(before: List<Block>, after: Block) {
    handlers.forEach { it.onRangesChanged(before, after) }
  }

  private fun onRangeShifted(before: Block, after: Block) {
    handlers.forEach { it.onRangeShifted(before, after) }
  }

  private fun afterBulkRangeChange(isDirty: Boolean) {
    handlers.forEach { it.afterBulkRangeChange(isDirty) }
  }
}

private class RangeChangeHandler {
  fun run(blocks: List<Block>,
          side: Side,
          startLine: Int,
          beforeLength: Int,
          afterLength: Int): Result {
    val endLine = startLine + beforeLength
    val rangeSizeDelta = afterLength - beforeLength

    val (beforeBlocks, affectedBlocks, afterBlocks) = sortRanges(blocks, side, startLine, endLine)

    val ourToOtherShift: Int = getOurToOtherShift(side, beforeBlocks)

    val newAffectedBlock = getNewAffectedBlock(side, startLine, endLine, rangeSizeDelta, ourToOtherShift,
                                               affectedBlocks)
    val newAfterBlocks = afterBlocks.map { it.shift(side, rangeSizeDelta) }

    val newBlocks = ArrayList<Block>(beforeBlocks.size + newAfterBlocks.size + 1)
    newBlocks.addAll(beforeBlocks)
    newBlocks.add(newAffectedBlock)
    newBlocks.addAll(newAfterBlocks)

    return Result(beforeBlocks, newBlocks,
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

  data class Result(val beforeBlocks: List<Block>, val newBlocks: List<Block>,
                    val affectedBlocks: List<Block>, val afterBlocks: List<Block>,
                    val newAffectedBlock: Block, val newAfterBlocks: List<Block>)
}

/**
 * We use line numbers in 3 documents:
 * A: Line number in unchanged document
 * B: Line number in changed document <before> the change
 * C: Line number in changed document <after> the change
 *
 * Algorithm is similar to building ranges for a merge conflict,
 * see [com.intellij.diff.comparison.ComparisonMergeUtil.FairMergeBuilder].
 * ie: B is the "Base" and A/C are "Left"/"Right". Old blocks hold the differences "A -> B",
 * changes from iterable hold the differences "B -> C". We want to construct new blocks with differences "A -> C.
 *
 * We iterate all differences in 'B' order, collecting interleaving groups of differences. Each group becomes a single newBlock.
 * [blockShift]/[changeShift] indicate how 'B' line is mapped to the 'A'/'C' lines at the start of current group.
 * [dirtyBlockShift]/[dirtyChangeShift] accumulate differences from the current group.
 *
 * block(otherSide -> side): A -> B
 * newBlock(otherSide -> side): A -> C
 * iterable: B -> C
 * dirtyStart, dirtyEnd: B
 * blockShift: delta B -> A
 * changeShift: delta B -> C
 */
private class BulkRangeChangeHandler(private val handlers: List<Handler>,
                                     private val blocks: List<Block>,
                                     private val side: Side) {
  private val newBlocks: MutableList<Block> = mutableListOf()

  private var dirtyStart = -1
  private var dirtyEnd = -1
  private val dirtyBlocks: MutableList<Block> = mutableListOf()
  private var dirtyBlocksModified = false

  private var blockShift: Int = 0
  private var changeShift: Int = 0
  private var dirtyBlockShift: Int = 0
  private var dirtyChangeShift: Int = 0

  fun run(iterable: DiffIterable): List<Block> {
    val it1 = PeekableIteratorWrapper(blocks.iterator())
    val it2 = PeekableIteratorWrapper(iterable.changes())

    while (it1.hasNext() || it2.hasNext()) {
      if (!it2.hasNext()) {
        handleBlock(it1.next())
        continue
      }
      if (!it1.hasNext()) {
        handleChange(it2.next())
        continue
      }

      val block = it1.peek()
      val range1 = block.range
      val range2 = it2.peek()

      if (range1.start(side) <= range2.start1) {
        handleBlock(it1.next())
      }
      else {
        handleChange(it2.next())
      }
    }
    flush(Int.MAX_VALUE)

    return newBlocks
  }

  private fun handleBlock(block: Block) {
    val range = block.range
    flush(range.start(side))

    dirtyBlockShift += getRangeDelta(range, side)

    markDirtyRange(range.start(side), range.end(side))

    dirtyBlocks.add(block)
  }

  private fun handleChange(range: Range) {
    flush(range.start1)

    dirtyChangeShift += getRangeDelta(range, Side.LEFT)

    markDirtyRange(range.start1, range.end1)

    dirtyBlocksModified = true
  }

  private fun markDirtyRange(start: Int, end: Int) {
    if (dirtyEnd == -1) {
      dirtyStart = start
      dirtyEnd = end
    }
    else {
      dirtyEnd = max(dirtyEnd, end)
    }
  }

  private fun flush(nextLine: Int) {
    if (dirtyEnd != -1 && dirtyEnd < nextLine) {
      if (dirtyBlocksModified) {
        val isTooBig = dirtyBlocks.any { it.isTooBig }
        val isDirty = true
        val range = createRange(side,
                                dirtyStart + changeShift, dirtyEnd + changeShift + dirtyChangeShift,
                                dirtyStart + blockShift, dirtyEnd + blockShift + dirtyBlockShift)
        val newBlock = Block(range, isDirty, isTooBig)
        onRangesChanged(dirtyBlocks, newBlock)
        newBlocks.add(newBlock)
      }
      else {
        assert(dirtyBlocks.size == 1)
        if (changeShift != 0) {
          for (oldBlock in dirtyBlocks) {
            val newBlock = oldBlock.shift(side, changeShift)
            onRangeShifted(oldBlock, newBlock)
            newBlocks.add(newBlock)
          }
        }
        else {
          newBlocks.addAll(dirtyBlocks)
        }
      }

      dirtyStart = -1
      dirtyEnd = -1
      dirtyBlocks.clear()
      dirtyBlocksModified = false

      blockShift += dirtyBlockShift
      changeShift += dirtyChangeShift
      dirtyBlockShift = 0
      dirtyChangeShift = 0
    }
  }

  private fun onRangesChanged(before: List<Block>, after: Block) {
    handlers.forEach { it.onRangesChanged(before, after) }
  }

  private fun onRangeShifted(before: Block, after: Block) {
    handlers.forEach { it.onRangeShifted(before, after) }
  }
}

private class BlocksRefresher(val handlers: List<Handler>,
                              val text1: CharSequence,
                              val text2: CharSequence,
                              val lineOffsets1: LineOffsets,
                              val lineOffsets2: LineOffsets,
                              val forceMergeNearbyBlocks: Boolean) {
  fun refresh(blocks: List<Block>, fastRefresh: Boolean): Result {
    val newBlocks = ArrayList<Block>()

    processMergeableGroups(blocks) { group ->
      if (group.any { it.isDirty }) {
        processMergedBlocks(group) { mergedBlock ->
          val freshBlocks = refreshMergedBlock(mergedBlock, fastRefresh)

          onRangeRefreshed(mergedBlock.merged, freshBlocks)

          newBlocks.addAll(freshBlocks)
        }
      }
      else {
        newBlocks.addAll(group)
      }
    }
    return Result(newBlocks)
  }

  private fun processMergeableGroups(blocks: List<Block>,
                                     processGroup: (group: List<Block>) -> Unit) {
    if (blocks.isEmpty()) return

    var i = 0
    var blockStart = 0
    while (i < blocks.size - 1) {
      if (!shouldMergeBlocks(blocks[i], blocks[i + 1])) {
        processGroup(blocks.subList(blockStart, i + 1))
        blockStart = i + 1
      }
      i += 1
    }
    processGroup(blocks.subList(blockStart, i + 1))
  }

  private fun shouldMergeBlocks(block1: Block, block2: Block): Boolean {
    if (forceMergeNearbyBlocks && block2.range.start2 - block1.range.end2 < NEARBY_BLOCKS_LINES) {
      return true
    }
    if (isWhitespaceOnlySeparated(block1, block2)) return true
    return false
  }

  private fun isWhitespaceOnlySeparated(block1: Block, block2: Block): Boolean {
    val range1 = DiffRangeUtil.getLinesRange(lineOffsets1, block1.range.start1, block1.range.end1, false)
    val range2 = DiffRangeUtil.getLinesRange(lineOffsets1, block2.range.start1, block2.range.end1, false)
    val start = range1.endOffset
    val end = range2.startOffset
    return trimStart(text1, start, end) == end
  }

  private fun processMergedBlocks(group: List<Block>,
                                  processBlock: (merged: MergedBlock) -> Unit) {
    assert(!group.isEmpty())

    var merged: Block? = null
    val original: MutableList<Block> = mutableListOf()

    for (block in group) {
      if (merged == null) {
        merged = block
        original += block
      }
      else {
        val newMerged = mergeBlocks(merged, block)
        if (newMerged != null) {
          merged = newMerged
          original += block
        }
        else {
          processBlock(MergedBlock(merged, original.toList()))
          original.clear()
          merged = block
          original += merged
        }
      }
    }

    processBlock(MergedBlock(merged!!, original.toList()))
  }

  private fun mergeBlocks(block1: Block, block2: Block): Block? {
    val isDirty = block1.isDirty || block2.isDirty
    val isTooBig = block1.isTooBig || block2.isTooBig
    val range = Range(block1.range.start1, block2.range.end1,
                      block1.range.start2, block2.range.end2)
    val merged = Block(range, isDirty, isTooBig)

    for (handler in handlers) {
      val success = handler.mergeRanges(block1, block2, merged)
      if (!success) return null // merging vetoed
    }
    return merged
  }

  private fun refreshMergedBlock(mergedBlock: MergedBlock, fastRefresh: Boolean): List<Block> {
    val freshBlocks = refreshBlock(mergedBlock.merged, fastRefresh)
    if (mergedBlock.original.size == 1) return freshBlocks
    if (!forceMergeNearbyBlocks) return freshBlocks

    // try reuse original blocks to prevent occasional 'insertion' moves
    val nonMergedFreshBlocks = mergedBlock.original.flatMap { block ->
      if (block.isDirty) {
        refreshBlock(block, fastRefresh)
      }
      else {
        listOf(block)
      }
    }

    val oldSize = calcNonWhitespaceSize(text1, text2, lineOffsets1, lineOffsets2, nonMergedFreshBlocks)
    val newSize = calcNonWhitespaceSize(text1, text2, lineOffsets1, lineOffsets2, freshBlocks)
    if (oldSize < newSize) return nonMergedFreshBlocks
    if (oldSize > newSize) return freshBlocks

    val oldTotalSize = calcSize(nonMergedFreshBlocks)
    val newTotalSize = calcSize(freshBlocks)
    if (oldTotalSize <= newTotalSize) return nonMergedFreshBlocks
    return freshBlocks
  }

  private fun refreshBlock(block: Block, fastRefresh: Boolean): List<Block> {
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

  private fun calcSize(blocks: List<Block>): Int {
    var result = 0
    for (block in blocks) {
      result += block.range.end1 - block.range.start1
      result += block.range.end2 - block.range.start2
    }
    return result
  }

  private fun calcNonWhitespaceSize(text1: CharSequence,
                                    text2: CharSequence,
                                    lineOffsets1: LineOffsets,
                                    lineOffsets2: LineOffsets,
                                    blocks: List<Block>): Int {
    var result = 0
    for (block in blocks) {
      for (line in block.range.start1 until block.range.end1) {
        if (!isWhitespaceLine(text1, lineOffsets1, line)) result++
      }
      for (line in block.range.start2 until block.range.end2) {
        if (!isWhitespaceLine(text2, lineOffsets2, line)) result++
      }
    }
    return result
  }

  private fun isWhitespaceLine(text: CharSequence, lineOffsets: LineOffsets, line: Int): Boolean {
    val start = lineOffsets.getLineStart(line)
    val end = lineOffsets.getLineEnd(line)
    return trimStart(text, start, end) == end
  }

  private fun onRangeRefreshed(before: Block, after: List<Block>) {
    handlers.forEach { it.onRangeRefreshed(before, after) }
  }

  data class Result(val newBlocks: List<Block>)
  data class MergedBlock(val merged: Block, val original: List<Block>)

  companion object {
    private const val NEARBY_BLOCKS_LINES = 30
  }
}

private fun getRangeDelta(range: Range, side: Side): Int {
  val otherSide = side.other()
  val deleted = range.end(side) - range.start(side)
  val inserted = range.end(otherSide) - range.start(otherSide)
  return inserted - deleted
}

private fun Block.shift(side: Side, delta: Int) = Block(
  shiftRange(this.range, side, delta), this.isDirty, this.isTooBig)

private fun shiftRange(range: Range, side: Side, shift: Int) = when {
  side.isLeft -> shiftRange(range, shift, 0)
  else -> shiftRange(range, 0, shift)
}

private fun shiftRange(range: Range, shift1: Int, shift2: Int) = Range(range.start1 + shift1,
                                                                       range.end1 + shift1,
                                                                       range.start2 + shift2,
                                                                       range.end2 + shift2)

private fun createRange(side: Side, start: Int, end: Int, otherStart: Int, otherEnd: Int): Range = when {
  side.isLeft -> Range(start, end, otherStart, otherEnd)
  else -> Range(otherStart, otherEnd, start, end)
}