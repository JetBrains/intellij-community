// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.merge

import com.intellij.diff.InvalidDiffRequestException
import com.intellij.diff.comparison.ComparisonMergeUtil
import com.intellij.diff.comparison.DiffTooBigException
import com.intellij.diff.contents.DocumentContent
import com.intellij.diff.fragments.MergeLineFragment
import com.intellij.diff.tools.util.base.IgnorePolicy
import com.intellij.diff.util.DiffUtil
import com.intellij.diff.util.LineRange
import com.intellij.diff.util.MergeConflictResolutionStrategy
import com.intellij.diff.util.MergeConflictType
import com.intellij.diff.util.Side
import com.intellij.diff.util.ThreeSide
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ReadOnlyModificationException
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.LineTokenizer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.EventDispatcher
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import it.unimi.dsi.fastutil.ints.IntList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.util.EventListener

/**
 * Handles all merge-related stuff
 * Doesn't know anything about the UI, merge viewer, editors, etc.
 *
 * Should call [rediff] after construction in order to be properly initialized
 */
@ApiStatus.Internal
class MergeConflictModel(
  private val project: Project?,
  private val mergeRequest: TextMergeRequest,
  val conflictResolver: LangSpecificMergeConflictResolverWrapper,
) : Disposable {
  private var mergeChanges: List<TextMergeChange> = emptyList()

  private val resultModel: MergeModelBase<TextMergeChange.State> = MergeModelImpl(project)

  private val textAutoResolver = TextAutoResolver(mergeRequest)
  private val mergeDiffBuilder = MergeDiffBuilder(project, mergeRequest, conflictResolver)

  private val dispatcher: EventDispatcher<MergeModelEventListener> = EventDispatcher.create(MergeModelEventListener::class.java)

  var precalculatedData: MergeDiffData? = null
    private set

  var contentModified: Boolean = false

  @RequiresBlockingContext
  @Throws(DiffTooBigException::class, InvalidDiffRequestException::class)
  fun rediffBlocking(
    ignorePolicy: IgnorePolicy,
    isAutoResolveImportConflicts: Boolean,
  ): MergeDiffData = runBlockingCancellable {
    rediff(ignorePolicy, isAutoResolveImportConflicts)
  }

  @Throws(DiffTooBigException::class, InvalidDiffRequestException::class)
  suspend fun rediff(
    ignorePolicy: IgnorePolicy,
    isAutoResolveImportConflicts: Boolean,
  ): MergeDiffData = withContext(Dispatchers.UiWithModelAccess) {
    val precalculated = precalculatedData
    if (precalculated != null &&
        precalculated.ignorePolicy == ignorePolicy &&
        precalculated.isAutoResolveImportConflicts == isAutoResolveImportConflicts) {
      return@withContext precalculated
    }

    precalculatedData = null
    mergeDiffBuilder.rediff(
      ignorePolicy = ignorePolicy,
      isAutoResolveImportConflicts = isAutoResolveImportConflicts).also { mergeDiffData ->
      val document = mergeRequest.getOutputContent().getDocument()
      val success = setInitialOutputContent(document, ThreeSide.BASE.select(mergeRequest.contents).document.immutableCharSequence)

      if (!success) {
        throw InvalidDiffRequestException(ReadOnlyModificationException(document, null))
      }
      else {
        contentModified = false
        precalculatedData = mergeDiffData
        initWithData(mergeDiffData)
      }
    }
  }

  @RequiresEdt
  private suspend fun setInitialOutputContent(document: Document, content: CharSequence): Boolean = edtWriteAction {
    DiffUtil.executeWriteCommand(document, project, DiffBundle.message("message.init.merge.content.command")) {
      document.setText(content)
      DiffUtil.putNonundoableOperation(project, document)
    }
  }

  fun addListener(listener: MergeModelEventListener, parentDisposable: Disposable): Unit =
    dispatcher.addListener(listener, parentDisposable)

  private fun initWithData(mergeDiffData: MergeDiffData) {
    val changeData = mergeDiffData.fragmentsWithMetadata.fragments.mapIndexed { i, fragment ->
      InitChangeData(fragment = fragment,
                     conflictType = mergeDiffData.conflictTypes[i],
                     isInImport = mergeDiffData.fragmentsWithMetadata.isIndexInImportRange(i))
    }
    val lineRanges = buildResultLineRanges(changeData)
    resultModel.setChanges(lineRanges)

    mergeChanges = buildMergeChanges(changeData, resultModel)
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

    return if (change.isConflict) {
      when (change.conflictType.resolutionStrategy) {
        MergeConflictResolutionStrategy.TEXT -> {
          val newContent = textAutoResolver.resolve(change) ?: return null
          replaceWithNewContent(change.index, newContent)
        }
        MergeConflictResolutionStrategy.SEMANTIC -> {
          val newContent = conflictResolver.getResolvedConflictContent(change.index) ?: return null
          replaceWithNewContent(change.index, newContent)
        }
        else -> null
      }
    }
    else {
      val masterSide: Side = side.select(Side.LEFT, if (change.isChange(Side.LEFT)) Side.LEFT else Side.RIGHT, Side.RIGHT)
      replaceChange(change.index, masterSide, false)
    }
  }

  @RequiresWriteLock
  fun replaceChangeWithAi(index: Int, newContentLines: List<String>): LineRange? {
    val change = getByIndex(index)
    if (change.isResolved) return null

    resultModel.replaceChange(change.index, newContentLines)
    change.markChangeResolvedWithAI()
    markChangeResolved(change.index)
    return LineRange(resultModel.getLineStart(change.index), resultModel.getLineEnd(change.index))
  }

  /**
   * Resets the resolved state of a specific change by restoring its base content
   * and invalidating the change in the result model. This action ensures the
   * change is marked as unresolved and its state is reverted to the original.
   *
   * @param index The index of the change to reset.
   * @param force If true, resets the change regardless of its resolved state. If false,
   *              only resets the change if it is already marked as resolved.
   */
  @RequiresWriteLock
  fun resetResolvedChange(index: Int, force: Boolean = false) {
    val change = getByIndex(index)
    if (!force && !change.isResolved) return
    val changeFragment = change.fragment
    val startLine = changeFragment.getStartLine(ThreeSide.BASE)
    val endLine = changeFragment.getEndLine(ThreeSide.BASE)

    val content = mergeRequest.getDocument(ThreeSide.BASE)
    val baseContent = DiffUtil.getLines(content, startLine, endLine)

    resultModel.replaceChange(change.index, baseContent)
    change.resetState()
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

  @RequiresWriteLock
  fun resetAllChanges() {
    getAllChanges().forEach { change: TextMergeChange -> resetResolvedChange(change.index, force = true) }
    mergeRequest.resetOutputContent()
  }

  @RequiresWriteLock
  fun replaceAllChanges(side: Side) {
    getAllChanges().forEach { change: TextMergeChange -> replaceChange(change.index, side, true) }
  }

  @RequiresWriteLock
  fun markAllChangesResolved() {
    getAllChanges().forEach { change: TextMergeChange -> markChangeResolved(change.index) }
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

    return if (change.isConflict) {
      side == ThreeSide.BASE &&
      change.conflictType.canBeResolved() &&
      !change.isResolved(Side.LEFT) &&
      !change.isResolved(Side.RIGHT) &&
      (change.conflictType.resolutionStrategy !== MergeConflictResolutionStrategy.TEXT || !isChangeRangeModified(change))
    }
    else {
      !change.isResolved &&
      change.isChange(side) &&
      !isChangeRangeModified(change)
    }
  }

  private fun TextMergeRequest.resetOutputContent() {
    val text = ThreeSide.BASE.select(contents).document.text
    outputContent.document.setText(text)
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

  fun executeMergeCommand(
    commandName: @NlsContexts.Command String?,
    commandGroupId: String?,
    undoPolicy: UndoConfirmationPolicy,
    bulkUpdate: Boolean,
    affectedIndexes: IntList?,
    task: Runnable,
  ): Boolean {
    return executeMergeCommand(commandName, commandGroupId, undoPolicy, bulkUpdate, affectedIndexes, task::run)
  }

  fun executeMergeCommand(
    commandName: @NlsContexts.Command String?,
    commandGroupId: String?,
    undoConfirmationPolicy: UndoConfirmationPolicy,
    bulkUpdate: Boolean,
    affectedIndexes: IntList?,
    task: () -> Unit,
  ): Boolean {
    contentModified = true
    return resultModel.executeMergeCommand(commandName, commandGroupId, undoConfirmationPolicy, bulkUpdate, affectedIndexes, task)
  }

  private fun getByIndex(index: Int): TextMergeChange {
    return mergeChanges[index]
  }

  private inner class MergeModelImpl(project: Project?) :
    MergeModelBase<TextMergeChange.State>(project, mergeRequest.outputContent.document) {
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
  private fun fireChangeSideResolved(change: TextMergeChange, side: Side) = fireEvent(MergeEvent.ChangeSideResolved(change, side))
  private fun fireChangeResolved(change: TextMergeChange) = fireEvent(MergeEvent.ChangeResolved(change))
  private fun fireChangeProcessed(change: TextMergeChange) = fireEvent(MergeEvent.ChangeProcessed(change))
  private fun fireBulkProcessingFinished() = fireEvent(MergeEvent.BulkProcessingFinished)
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
sealed class MergeEvent {
  @ApiStatus.Internal
  class ChangeResolved(val change: TextMergeChange) : MergeEvent()

  @ApiStatus.Internal
  class ChangeSideResolved(val change: TextMergeChange, val side: Side) : MergeEvent()

  @ApiStatus.Internal
  class ChangeProcessed(val change: TextMergeChange) : MergeEvent()

  @ApiStatus.Internal
  object BulkProcessingFinished : MergeEvent()
}

private class TextAutoResolver(private val mergeRequest: TextMergeRequest) {

  fun resolve(change: TextMergeChange): CharSequence? {
    assert(change.conflictType.resolutionStrategy === MergeConflictResolutionStrategy.TEXT)

    val texts: List<CharSequence> = ThreeSide.map { side: ThreeSide ->
      DiffUtil.getLinesContent(mergeRequest.getDocumentWithOutputAsBase(side), change.getStartLine(side), change.getEndLine(side))
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
private fun TextMergeRequest.getDocumentWithOutputAsBase(side: ThreeSide): Document =
  if (side == ThreeSide.BASE) outputContent.document else side.select(contents).document

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