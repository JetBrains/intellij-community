// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.merge

import com.intellij.diff.util.DiffUtil
import com.intellij.diff.util.LineRange
import com.intellij.openapi.Disposable
import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.command.undo.BasicUndoableAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.util.SmartList
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntList
import it.unimi.dsi.fastutil.ints.IntLists
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.ints.IntSet
import org.jetbrains.annotations.ApiStatus
import java.lang.ref.WeakReference
import java.util.function.IntConsumer
import kotlin.math.max
import kotlin.math.min

@ApiStatus.Internal
abstract class MergeModelBase<S : MergeModelBase.State>(
  private val project: Project?,
  private val document: Document,
) : Disposable {
  private val undoManager: UndoManager = if (project != null) UndoManager.getInstance(project) else UndoManager.getGlobalInstance()

  private val startLines: IntList = IntArrayList()
  private val endLines: IntList = IntArrayList()

  private val changesCount: Int
    get() = startLines.size

  private val changesToUpdate: IntSet = IntOpenHashSet()
  private var bulkChangeUpdateDepth = 0

  @get:RequiresEdt
  private var isInsideCommand = false

  private var isDisposed = false

  init {
    document.addDocumentListener(DocumentListener(), this)
  }

  @RequiresEdt
  override fun dispose() {
    if (isDisposed) return
    isDisposed = true

    LOG.assertTrue(bulkChangeUpdateDepth == 0)

    clearAll()
  }

  fun getLineStart(index: Int): Int = startLines.getInt(index)

  fun getLineEnd(index: Int): Int = endLines.getInt(index)

  fun setChanges(changes: List<LineRange>) {
    clearAll()

    for (range in changes) {
      startLines.add(range.start)
      endLines.add(range.end)
    }
  }

  private fun setLineStart(index: Int, line: Int) {
    startLines.set(index, line)
  }

  private fun setLineEnd(index: Int, line: Int) {
    endLines.set(index, line)
  }

  private fun clearAll() {
    startLines.clear()
    endLines.clear()
  }

  //
  // Repaint
  //
  @RequiresEdt
  fun invalidateChange(index: Int) {
    if (bulkChangeUpdateDepth > 0) {
      changesToUpdate.add(index)
    }
    else {
      onChangeUpdated(index)
    }
  }

  @RequiresEdt
  private fun enterBulkChangeUpdateBlock() {
    bulkChangeUpdateDepth++
  }

  @RequiresEdt
  private fun exitBulkChangeUpdateBlock() {
    bulkChangeUpdateDepth--
    LOG.assertTrue(bulkChangeUpdateDepth >= 0)

    if (bulkChangeUpdateDepth == 0) {
      changesToUpdate.forEach(IntConsumer { index: Int ->
        onChangeUpdated(index)
      })
      changesToUpdate.clear()
      onBulkUpdateFinished()
    }
  }

  @RequiresEdt
  protected abstract fun onChangeUpdated(index: Int)

  @RequiresEdt
  protected open fun onBulkUpdateFinished() {

  }

  //
  // Undo
  //
  @RequiresEdt
  protected abstract fun storeChangeState(index: Int): S

  @RequiresEdt
  protected open fun restoreChangeState(state: S) {
    setLineStart(state.index, state.startLine)
    setLineEnd(state.index, state.endLine)
  }

  @RequiresEdt
  protected open fun processDocumentChange(index: Int, oldLine1: Int, oldLine2: Int, shift: Int): S? {
    val line1 = getLineStart(index)
    val line2 = getLineEnd(index)

    val newRange = DiffUtil.updateRangeOnModification(line1, line2, oldLine1, oldLine2, shift)

    // RangeMarker can be updated in a different way
    val rangeAffected = newRange.damaged || (oldLine2 >= line1 && oldLine1 <= line2)

    val rangeManuallyEdit = newRange.damaged || (oldLine2 > line1 && oldLine1 < line2)
    if (rangeManuallyEdit && !isInsideCommand && !undoManager.isUndoOrRedoInProgress) {
      onRangeManuallyEdit(index)
    }

    val oldState = if (rangeAffected) storeChangeState(index) else null

    setLineStart(index, newRange.startLine)
    setLineEnd(index, newRange.endLine)

    return oldState
  }

  @ApiStatus.Internal
  protected open fun onRangeManuallyEdit(index: Int) {
  }

  private inner class DocumentListener : com.intellij.openapi.editor.event.DocumentListener {
    override fun beforeDocumentChange(e: DocumentEvent) {
      if (isDisposed) return
      enterBulkChangeUpdateBlock()

      if (changesCount == 0) return

      val lineRange = DiffUtil.getAffectedLineRange(e)
      val shift = DiffUtil.countLinesShift(e)

      val corruptedStates: MutableList<S> = SmartList<S>()
      for (index in 0..<changesCount) {
        val oldState = processDocumentChange(index, lineRange.start, lineRange.end, shift)
        if (oldState == null) continue

        invalidateChange(index)
        if (!isInsideCommand) corruptedStates.add(oldState)
      }

      if (!corruptedStates.isEmpty()) {
        // document undo is registered inside onDocumentChange, so our undo() will be called after its undo().
        // thus thus we can avoid checks for isUndoInProgress() (to avoid modification of the same TextMergeChange by this listener)
        undoManager.undoableActionPerformed(UndoableAction(this@MergeModelBase, corruptedStates, true))
      }
    }

    override fun documentChanged(e: DocumentEvent) {
      if (isDisposed) return
      exitBulkChangeUpdateBlock()
    }
  }

  fun executeMergeCommand(
    commandName: @NlsContexts.Command String?,
    commandGroupId: String?,
    confirmationPolicy: UndoConfirmationPolicy,
    underBulkUpdate: Boolean,
    affectedChanges: IntList?,
    task: Runnable,
  ): Boolean {
    val allAffectedChanges = if (affectedChanges != null) collectAffectedChanges(affectedChanges) else IntLists.emptyList()
    return DiffUtil.executeWriteCommand(project, document, commandName, commandGroupId, confirmationPolicy, underBulkUpdate, Runnable {
      LOG.assertTrue(!isInsideCommand)
      // We should restore states after changes in document (by DocumentUndoProvider) to avoid corruption by our onBeforeDocumentChange()
      // Undo actions are performed in backward order, while redo actions are performed in forward order.
      // Thus we should register two UndoableActions.
      isInsideCommand = true
      enterBulkChangeUpdateBlock()
      try {
        registerUndoRedo(true, allAffectedChanges)
        try {
          task.run()
        }
        finally {
          registerUndoRedo(false, allAffectedChanges)
        }
      }
      finally {
        exitBulkChangeUpdateBlock()
        isInsideCommand = false
      }
    })
  }

  private fun registerUndoRedo(undo: Boolean, affectedChanges: IntList) {
    val states: MutableList<S>
    if (affectedChanges.isNotEmpty()) {
      states = ArrayList(affectedChanges.size)
      affectedChanges.forEach(IntConsumer { index: Int ->
        states.add(storeChangeState(index))
      })
    }
    else {
      states = ArrayList(changesCount)
      for (index in 0..<changesCount) {
        states.add(storeChangeState(index))
      }
    }
    undoManager.undoableActionPerformed(UndoableAction(this, states, undo))
  }

  private class UndoableAction(
    model: MergeModelBase<*>,
    private val states: List<State>,
    private val isUndo: Boolean,
  ) : BasicUndoableAction(model.document) {
    private val modelRef: WeakReference<MergeModelBase<*>> = WeakReference<MergeModelBase<*>>(model)

    override fun undo() {
      val model = modelRef.get() ?: return
      if (isUndo) restoreStates(model)
    }

    override fun redo() {
      val model = modelRef.get() ?: return
      if (!isUndo) restoreStates(model)
    }

    private fun restoreStates(model: MergeModelBase<*>) {
      if (model.isDisposed) return
      if (model.changesCount == 0) return

      model.enterBulkChangeUpdateBlock()
      try {
        model as MergeModelBase<State>
        for (state in states) {
          model.restoreChangeState(state)
          model.invalidateChange(state.index)
        }
      }
      finally {
        model.exitBulkChangeUpdateBlock()
      }
    }
  }

  //
  // Actions
  //
  @RequiresWriteLock
  fun replaceChange(index: Int, newContent: List<CharSequence>) {
    LOG.assertTrue(isInsideCommand)
    val outputStartLine = getLineStart(index)
    val outputEndLine = getLineEnd(index)

    DiffUtil.applyModification(document, outputStartLine, outputEndLine, newContent)

    if (outputStartLine == outputEndLine) { // onBeforeDocumentChange() should process other cases correctly
      val newOutputEndLine = outputStartLine + newContent.size
      moveChangesAfterInsertion(index, outputStartLine, newOutputEndLine)
    }
  }

  @RequiresWriteLock
  fun appendChange(index: Int, newContent: List<CharSequence>) {
    LOG.assertTrue(isInsideCommand)
    val outputStartLine = getLineStart(index)
    val outputEndLine = getLineEnd(index)

    DiffUtil.applyModification(document, outputEndLine, outputEndLine, newContent)

    val newOutputEndLine = outputEndLine + newContent.size
    moveChangesAfterInsertion(index, outputStartLine, newOutputEndLine)
  }

  /*
   * We want to include inserted block into change, so we are updating endLine(BASE).
   *
   * It could break order of changes if there are other changes that starts/ends at this line.
   * So we should check all other changes and shift them if necessary.
   */
  private fun moveChangesAfterInsertion(
    index: Int,
    newOutputStartLine: Int,
    newOutputEndLine: Int,
  ) {
    LOG.assertTrue(isInsideCommand)

    if (getLineStart(index) != newOutputStartLine || getLineEnd(index) != newOutputEndLine) {
      setLineStart(index, newOutputStartLine)
      setLineEnd(index, newOutputEndLine)
      invalidateChange(index)
    }

    var beforeChange = true
    for (otherIndex in 0..<changesCount) {
      val startLine = getLineStart(otherIndex)
      val endLine = getLineEnd(otherIndex)
      if (endLine < newOutputStartLine) continue
      if (startLine > newOutputEndLine) break
      if (index == otherIndex) {
        beforeChange = false
        continue
      }

      val newStartLine = if (beforeChange) min(startLine, newOutputStartLine) else newOutputEndLine
      val newEndLine = if (beforeChange) min(endLine, newOutputStartLine) else max(endLine, newOutputEndLine)
      if (startLine != newStartLine || endLine != newEndLine) {
        setLineStart(otherIndex, newStartLine)
        setLineEnd(otherIndex, newEndLine)
        invalidateChange(otherIndex)
      }
    }
  }

  /*
   * Nearby changes could be affected as well (ex: by moveChangesAfterInsertion)
   *
   * null means all changes could be affected
   */
  @RequiresEdt
  private fun collectAffectedChanges(directChanges: IntList): IntList {
    val result: IntList = IntArrayList(directChanges.size)

    var directArrayIndex = 0
    var otherIndex = 0
    while (directArrayIndex < directChanges.size && otherIndex < changesCount) {
      val directIndex = directChanges.getInt(directArrayIndex)

      if (directIndex == otherIndex) {
        result.add(directIndex)
        otherIndex++
        continue
      }

      val directStart = getLineStart(directIndex)
      val directEnd = getLineEnd(directIndex)
      val otherStart = getLineStart(otherIndex)
      val otherEnd = getLineEnd(otherIndex)

      if (otherEnd < directStart) {
        otherIndex++
        continue
      }
      if (otherStart > directEnd) {
        directArrayIndex++
        continue
      }

      result.add(otherIndex)
      otherIndex++
    }

    LOG.assertTrue(directChanges.size <= result.size)
    return result
  }

  //
  // Helpers
  //
  open class State(val index: Int, val startLine: Int, val endLine: Int)

  companion object {
    private val LOG = Logger.getInstance(MergeModelBase::class.java)
  }
}

