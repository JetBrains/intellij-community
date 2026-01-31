// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.merge

import com.intellij.diff.comparison.ComparisonMergeUtil
import com.intellij.diff.contents.DocumentContent
import com.intellij.diff.fragments.MergeLineFragment
import com.intellij.diff.util.DiffUtil
import com.intellij.diff.util.LineRange
import com.intellij.diff.util.MergeConflictResolutionStrategy
import com.intellij.diff.util.MergeConflictType
import com.intellij.diff.util.Side
import com.intellij.diff.util.ThreeSide
import com.intellij.openapi.Disposable
import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.LineTokenizer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.EventDispatcher
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import it.unimi.dsi.fastutil.ints.IntList
import org.jetbrains.annotations.ApiStatus
import java.util.EventListener

/**
 * Handles all merge-related stuff
 * Doesn't know anything about the UI, merge viewer, editors, etc.
 */
@ApiStatus.Internal
class MergeConflictModel(
  project: Project?,
  private val mergeRequest: TextMergeRequest,
) : Disposable {
  private var mergeChanges: List<TextMergeChange> = emptyList()

  private val resultModel: MergeModelBase<TextMergeChange.State> = MergeModelImpl(project)

  private val textAutoResolver: TextAutoResolver = TextAutoResolver(mergeRequest)

  private val dispatcher: EventDispatcher<MergeModelEventListener> = EventDispatcher.create(MergeModelEventListener::class.java)

  fun addListener(listener: MergeModelEventListener, parentDisposable: Disposable): Unit = dispatcher.addListener(listener, parentDisposable)

  fun initWithData(changesDatas: List<InitChangeData>) {
    clear()

    val lineRanges = buildResultLineRanges(changesDatas)
    resultModel.setChanges(lineRanges)

    mergeChanges = buildMergeChanges(changesDatas, resultModel)
  }

  fun getAllChanges(): List<TextMergeChange> = mergeChanges.toList()
  fun getUnresolvedChanges(): List<TextMergeChange> = mergeChanges.filterNot { it.isResolved }
  fun getAutoResolvableChanges(): List<TextMergeChange> = mergeChanges.filter { canResolveChangeAutomatically(it.index, ThreeSide.BASE) }
  fun getImportChanges(): List<TextMergeChange> = mergeChanges.filter { it.isImportChange }
  fun getSemanticallyResolvableChanges(): List<TextMergeChange> = getAutoResolvableChanges()
    .filter { it.conflictType.resolutionStrategy === MergeConflictResolutionStrategy.SEMANTIC }

  fun hasNonConflictedChanges(side: ThreeSide): Boolean = mergeChanges.any { change: TextMergeChange ->
    !change.isConflict && canResolveChangeAutomatically(change.index, side)
  }

  fun hasAutoResolvableConflictedChanges(): Boolean = mergeChanges.any { change: TextMergeChange ->
    canResolveChangeAutomatically(change.index, ThreeSide.BASE)
  }

  fun clear() {
    mergeChanges = emptyList()
    resultModel.setChanges(emptyList())
  }

  @RequiresEdt
  fun ignoreChange(index: Int, side: Side, resolveChange: Boolean) {
    val change = getByIndex(index)
    if (!change.isConflict || resolveChange) {
      markChangeResolved(change.index)
    }
    else {
      markChangeResolved(change, side)
    }
  }

  @RequiresWriteLock
  fun resolveChangeAutomatically(index: Int, side: ThreeSide): LineRange? {
    val change = getByIndex(index)

    if (!canResolveChangeAutomatically(change.index, side)) return null

    if (change.isConflict) {
      val resolutionStrategy = change.conflictType.resolutionStrategy ?: return null

      return when (resolutionStrategy) {
        MergeConflictResolutionStrategy.TEXT -> {
          val newContent = textAutoResolver.resolve(change) ?: return null
          return replaceWithNewContent(change.index, newContent)
        }
        else -> null
      }
    }

    val masterSide: Side = side.select(Side.LEFT, if (change.isChange(Side.LEFT)) Side.LEFT else Side.RIGHT, Side.RIGHT)
    return replaceChange(change.index, masterSide, false)
  }

  @RequiresWriteLock
  fun replaceChangeWithAi(index: Int, newContentLines: List<String>): LineRange? {
    val change = getByIndex(index)
    if (change.isResolved) return null

    resultModel.replaceChange(change.index, newContentLines)
    //myAggregator.wasResolvedByAi(change.index)
    change.markChangeResolvedWithAI()
    markChangeResolved(change.index)
    return LineRange(resultModel.getLineStart(change.index), resultModel.getLineEnd(change.index))
  }

  @RequiresWriteLock
  fun resetResolvedChange(index: Int) {
    val change = getByIndex(index)
    if (!change.isResolved) return
    val changeFragment = change.fragment
    val startLine = changeFragment.getStartLine(ThreeSide.BASE)
    val endLine = changeFragment.getEndLine(ThreeSide.BASE)

    val content = mergeRequest.getDocument(ThreeSide.BASE)
    val baseContent = DiffUtil.getLines(content, startLine, endLine)

    resultModel.replaceChange(change.index, baseContent)

    change.resetState()
    //if (change.isResolvedWithAI) {
    //  myAggregator.wasRolLEDBackAfterAI(change.index)
    //}
    markChangeResolved(change.index)
    resultModel.invalidateChange(change.index)
  }

  @RequiresWriteLock
  fun replaceWithNewContent(index: Int, newContent: CharSequence): LineRange {
    val change = getByIndex(index)
    val newContentLines: Array<String> = LineTokenizer.tokenize(newContent, false)
    resultModel.replaceChange(change.index, listOf(*newContentLines))
    markChangeResolved(change.index)
    return LineRange(resultModel.getLineStart(change.index), resultModel.getLineEnd(change.index))
  }

  @RequiresWriteLock
  fun replaceChange(index: Int, side: Side, resolveChange: Boolean): LineRange? {
    val change = getByIndex(index)
    if (change.isResolved(side)) return null
    if (!change.isChange(side)) {
      markChangeResolved(change.index)
      return null
    }

    val sourceSide = side.select(ThreeSide.LEFT, ThreeSide.RIGHT)
    val oppositeSide = side.select(ThreeSide.RIGHT, ThreeSide.LEFT)

    val sourceDocument: Document = mergeRequest.getDocument(sourceSide)
    val sourceStartLine = change.getStartLine(sourceSide)
    val sourceEndLine = change.getEndLine(sourceSide)
    val newContent = DiffUtil.getLines(sourceDocument, sourceStartLine, sourceEndLine)

    val newLineStart: Int
    if (change.isConflict) {
      val append = change.isOnesideAppliedConflict
      if (append) {
        newLineStart = resultModel.getLineEnd(change.index)
        resultModel.appendChange(change.index, newContent)
      }
      else {
        resultModel.replaceChange(change.index, newContent)
        newLineStart = resultModel.getLineStart(change.index)
      }

      if (resolveChange || change.getStartLine(oppositeSide) == change.getEndLine(oppositeSide)) {
        markChangeResolved(change.index)
      }
      else {
        change.markOnesideAppliedConflict()
        markChangeResolved(change, side)
      }
    }
    else {
      resultModel.replaceChange(change.index, newContent)
      newLineStart = resultModel.getLineStart(change.index)
      markChangeResolved(change.index)
    }
    val newLineEnd: Int = resultModel.getLineEnd(change.index)
    return LineRange(newLineStart, newLineEnd)
  }

  @RequiresEdt
  private fun markChangeResolved(change: TextMergeChange, side: Side) {
    if (change.isResolved) return
    change.setResolved(side, true)
    fireChangeSideResolved(change, side)
    if (change.isResolved) {
      fireChangeResolved(change)
    }
    resultModel.invalidateChange(change.index)
  }

  @RequiresEdt
  fun markChangeResolved(changeIndex: Int) {
    val change = getByIndex(changeIndex)
    if (change.isResolved) return
    change.setResolved(Side.LEFT, true)
    change.setResolved(Side.RIGHT, true)
    fireChangeResolved(change)
    resultModel.invalidateChange(change.index)
  }

  fun canResolveChangeAutomatically(changeIndex: Int, side: ThreeSide): Boolean {
    val change = getByIndex(changeIndex)
    
    if (change.isConflict) {
      return side == ThreeSide.BASE &&
             change.conflictType.canBeResolved() &&
             !change.isResolved(Side.LEFT) && !change.isResolved(Side.RIGHT) &&
             (change.conflictType.resolutionStrategy !== MergeConflictResolutionStrategy.TEXT || !isChangeRangeModified(change))
    }

    return !change.isResolved &&
           change.isChange(side) && !isChangeRangeModified(change)

  }

  private fun isChangeRangeModified(change: TextMergeChange): Boolean {
    val changeFragment = change.fragment
    val baseStartLine = changeFragment.getStartLine(ThreeSide.BASE)
    val baseEndLine = changeFragment.getEndLine(ThreeSide.BASE)
    val baseDiffContent: DocumentContent = ThreeSide.BASE.select<DocumentContent>(mergeRequest.contents)
    val baseDocument = baseDiffContent.getDocument()

    val resultStartLine = change.resultStartLine
    val resultEndLine = change.resultEndLine

    val baseContent = DiffUtil.getLinesContent(baseDocument, baseStartLine, baseEndLine)
    val resultContent = DiffUtil.getLinesContent(mergeRequest.outputContent.document, resultStartLine, resultEndLine)
    return !StringUtil.equals(baseContent, resultContent)
  }

  override fun dispose() {
    Disposer.dispose(resultModel)
  }

  fun executeMergeCommand(commandName: @NlsContexts.Command String?, string: String?, undoPolicy: UndoConfirmationPolicy, bulkUpdate: Boolean, affectedIndexes: IntList?, task: Runnable): Boolean {
    return executeMergeCommand(commandName, string, undoPolicy, bulkUpdate, affectedIndexes, task::run)
  }

  fun executeMergeCommand(commandName: @NlsContexts.Command String?, string: String?, undoConfirmationPolicy: UndoConfirmationPolicy, bulkUpdate: Boolean, affectedIndexes: IntList?, task: () -> Unit): Boolean {
    return resultModel.executeMergeCommand(commandName, string, undoConfirmationPolicy, bulkUpdate, affectedIndexes, task)
  }

  private fun getByIndex(index: Int): TextMergeChange {
    return mergeChanges[index]
  }

  private inner class MergeModelImpl(project: Project?) : MergeModelBase<TextMergeChange.State>(project, mergeRequest.outputContent.document) {
    override fun onChangeUpdated(index: Int) = fireChangeProcessed(getByIndex(index))
    override fun onBulkUpdateFinished() = fireBulkProcessingFinished()

    override fun storeChangeState(index: Int): TextMergeChange.State = getByIndex(index).storeState()

    override fun restoreChangeState(state: TextMergeChange.State) {
      super.restoreChangeState(state)
      val change: TextMergeChange = getByIndex(state.index)

      val wasResolved = change.isResolved

      change.restoreState(state)
      if (wasResolved != change.isResolved) fireChangeResolved(change)
    }

    override fun processDocumentChange(index: Int, oldLine1: Int, oldLine2: Int, shift: Int): TextMergeChange.State? {
      val state = super.processDocumentChange(index, oldLine1, oldLine2, shift)

      val change = getByIndex(index)
      if (change.resultStartLine == change.resultEndLine && change.conflictType.type == MergeConflictType.Type.DELETED && !change.isResolved) {
        markChangeResolved(change.index)
      }

      return state
    }
  }

  private fun fireEvent(event: MergeEvent) = dispatcher.multicaster.onMergeEvent(event)
  private fun fireChangeSideResolved(change: TextMergeChange, side: Side) = fireEvent(ChangeSideResolved(change, side))
  private fun fireChangeResolved(change: TextMergeChange) = fireEvent(ChangeResolved(change))
  private fun fireChangeProcessed(change: TextMergeChange) = fireEvent(ChangeProcessed(change))
  private fun fireBulkProcessingFinished() = fireEvent(BulkProcessingFinished)
}

@ApiStatus.Internal
data class InitChangeData(
  val fragment: MergeLineFragment,
  val conflictType: MergeConflictType,
  val isInImport: Boolean,
)

@ApiStatus.Internal
fun interface MergeModelEventListener : EventListener {
  fun onMergeEvent(event: MergeEvent)
}

@ApiStatus.Internal
sealed class MergeEvent()

@ApiStatus.Internal
class ChangeResolved(val change: TextMergeChange) : MergeEvent()

@ApiStatus.Internal
class ChangeSideResolved(val change: TextMergeChange, val side: Side) : MergeEvent()

@ApiStatus.Internal
class ChangeProcessed(val change: TextMergeChange) : MergeEvent()

@ApiStatus.Internal
object BulkProcessingFinished : MergeEvent()

private class TextAutoResolver(private val mergeRequest: TextMergeRequest) {

  fun resolve(change: TextMergeChange): CharSequence? {
    assert(change.conflictType.resolutionStrategy === MergeConflictResolutionStrategy.TEXT)

    val texts: List<CharSequence> = ThreeSide.map { side: ThreeSide ->
      DiffUtil.getLinesContent(mergeRequest.getDocument(side), change.getStartLine(side), change.getEndLine(side))
    }

    val leftText: CharSequence = ThreeSide.LEFT.select(texts)
    val baseText: CharSequence = ThreeSide.BASE.select(texts)
    val rightText: CharSequence = ThreeSide.RIGHT.select(texts)
    val newContent = ComparisonMergeUtil.tryResolveConflict(leftText, baseText, rightText)

    if (newContent == null) {
      LOG.warn(String.format("Can't resolve conflicting change:\n'%s'\n'%s'\n'%s'\n", leftText, baseText, rightText))
    }

    return newContent
  }

  companion object {
    private val LOG = logger<TextAutoResolver>()
  }
}

private fun TextMergeRequest.getDocument(side: ThreeSide): Document = side.select(contents).document

private fun buildResultLineRanges(changesDatas: List<InitChangeData>): List<LineRange> = changesDatas
  .map {
    val fragment = it.fragment
    LineRange(fragment.getStartLine(ThreeSide.BASE), fragment.getEndLine(ThreeSide.BASE))
  }

private fun buildMergeChanges(
  changesDatas: List<InitChangeData>,
  resultModel: MergeModelBase<TextMergeChange.State>,
): List<TextMergeChange> = changesDatas
  .mapIndexed { index, data ->
    TextMergeChange(index, data.isInImport, data.fragment, data.conflictType, resultModel)
  }