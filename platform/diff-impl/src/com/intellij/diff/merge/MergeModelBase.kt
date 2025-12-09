// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.util.SmartList
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntList
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.ints.IntSet
import org.jetbrains.annotations.ApiStatus
import java.lang.ref.WeakReference
import java.util.function.IntConsumer
import kotlin.math.max
import kotlin.math.min

@ApiStatus.Internal
abstract class MergeModelBase<S : MergeModelBase.State?>(private val myProject: Project?, private val myDocument: Document) : Disposable {
  private val myUndoManager: UndoManager?

  private val myStartLines: IntList = IntArrayList()
  private val myEndLines: IntList = IntArrayList()

  private val myChangesToUpdate: IntSet = IntOpenHashSet()
  private var myBulkChangeUpdateDepth = 0

  @get:RequiresEdt
  var isInsideCommand: Boolean = false
    private set

  var isDisposed: Boolean = false
    private set

  init {
    myUndoManager = if (myProject != null) UndoManager.getInstance(myProject) else UndoManager.getGlobalInstance()

    myDocument.addDocumentListener(MergeModelBase.MyDocumentListener(), this)
  }

  @RequiresEdt
  override fun dispose() {
    if (this.isDisposed) return
    this.isDisposed = true

    LOG.assertTrue(myBulkChangeUpdateDepth == 0)

    myStartLines.clear()
    myEndLines.clear()
  }

  val changesCount: Int
    get() = myStartLines.size

  fun getLineStart(index: Int): Int {
    return myStartLines.getInt(index)
  }

  fun getLineEnd(index: Int): Int {
    return myEndLines.getInt(index)
  }

  fun setChanges(changes: MutableList<out LineRange>) {
    myStartLines.clear()
    myEndLines.clear()

    for (range in changes) {
      myStartLines.add(range.start)
      myEndLines.add(range.end)
    }
  }

  private fun setLineStart(index: Int, line: Int) {
    myStartLines.set(index, line)
  }

  private fun setLineEnd(index: Int, line: Int) {
    myEndLines.set(index, line)
  }

  //
  // Repaint
  //
  @RequiresEdt
  fun invalidateHighlighters(index: Int) {
    if (myBulkChangeUpdateDepth > 0) {
      myChangesToUpdate.add(index)
    }
    else {
      reinstallHighlighters(index)
    }
  }

  @RequiresEdt
  fun enterBulkChangeUpdateBlock() {
    myBulkChangeUpdateDepth++
  }

  @RequiresEdt
  fun exitBulkChangeUpdateBlock() {
    myBulkChangeUpdateDepth--
    LOG.assertTrue(myBulkChangeUpdateDepth >= 0)

    if (myBulkChangeUpdateDepth == 0) {
      myChangesToUpdate.forEach(IntConsumer { index: Int ->
        reinstallHighlighters(index)
      })
      myChangesToUpdate.clear()
      postInstallHighlighters()
    }
  }

  @RequiresEdt
  protected abstract fun reinstallHighlighters(index: Int)

  @RequiresEdt
  protected open fun postInstallHighlighters() {
  }

  //
  // Undo
  //
  @RequiresEdt
  protected abstract fun storeChangeState(index: Int): S?

  @RequiresEdt
  protected open fun restoreChangeState(state: S) {
    setLineStart(state!!.myIndex, state.myStartLine)
    setLineEnd(state.myIndex, state.myEndLine)
  }

  @RequiresEdt
  protected open fun processDocumentChange(index: Int, oldLine1: Int, oldLine2: Int, shift: Int): S? {
    val line1 = getLineStart(index)
    val line2 = getLineEnd(index)

    val newRange = DiffUtil.updateRangeOnModification(line1, line2, oldLine1, oldLine2, shift)

    // RangeMarker can be updated in a different way
    val rangeAffected = newRange.damaged || (oldLine2 >= line1 && oldLine1 <= line2)

    val rangeManuallyEdit = newRange.damaged || (oldLine2 > line1 && oldLine1 < line2)
    if (rangeManuallyEdit && !this.isInsideCommand && (myUndoManager != null && !myUndoManager.isUndoOrRedoInProgress())) {
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

  private inner class MyDocumentListener : DocumentListener {
    override fun beforeDocumentChange(e: DocumentEvent) {
      if (this.isDisposed) return
      enterBulkChangeUpdateBlock()

      if (this.changesCount == 0) return

      val lineRange = DiffUtil.getAffectedLineRange(e)
      val shift = DiffUtil.countLinesShift(e)

      val corruptedStates: MutableList<S?> = SmartList<S?>()
      for (index in 0..<this.changesCount) {
        val oldState = processDocumentChange(index, lineRange.start, lineRange.end, shift)
        if (oldState == null) continue

        invalidateHighlighters(index)
        if (!this.isInsideCommand) corruptedStates.add(oldState)
      }

      if (myUndoManager != null && !corruptedStates.isEmpty()) {
        // document undo is registered inside onDocumentChange, so our undo() will be called after its undo().
        // thus thus we can avoid checks for isUndoInProgress() (to avoid modification of the same TextMergeChange by this listener)
        myUndoManager.undoableActionPerformed(MergeModelBase.MyUndoableAction(this@MergeModelBase, corruptedStates, true))
      }
    }

    override fun documentChanged(e: DocumentEvent) {
      if (this.isDisposed) return
      exitBulkChangeUpdateBlock()
    }
  }


  fun executeMergeCommand(
    @NlsContexts.Command commandName: @NlsContexts.Command String?,
    commandGroupId: String?,
    confirmationPolicy: UndoConfirmationPolicy,
    underBulkUpdate: Boolean,
    affectedChanges: IntList?,
    task: Runnable,
  ): Boolean {
    val allAffectedChanges = if (affectedChanges != null) collectAffectedChanges(affectedChanges) else null
    return DiffUtil.executeWriteCommand(
      myProject,
      myDocument,
      commandName,
      commandGroupId,
      confirmationPolicy,
      underBulkUpdate,
      Runnable {
        LOG.assertTrue(!this.isInsideCommand)
        // We should restore states after changes in document (by DocumentUndoProvider) to avoid corruption by our onBeforeDocumentChange()
        // Undo actions are performed in backward order, while redo actions are performed in forward order.
        // Thus we should register two UndoableActions.
        this.isInsideCommand = true
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
          this.isInsideCommand = false
        }
      })
  }

  private fun registerUndoRedo(undo: Boolean, affectedChanges: IntList?) {
    if (myUndoManager == null) return

    val states: MutableList<S?>?
    if (affectedChanges != null) {
      states = ArrayList<S?>(affectedChanges.size)
      affectedChanges.forEach(IntConsumer { index: Int ->
        states.add(storeChangeState(index))
      })
    }
    else {
      states = ArrayList<S?>(this.changesCount)
      for (index in 0..<this.changesCount) {
        states.add(storeChangeState(index))
      }
    }
    myUndoManager.undoableActionPerformed(MergeModelBase.MyUndoableAction(this, states, undo))
  }

  private class MyUndoableAction(model: MergeModelBase<*>, private val myStates: MutableList<out State>, private val myUndo: Boolean) :
    BasicUndoableAction(model.myDocument) {
    private val myModelRef: WeakReference<MergeModelBase<*>?>

    init {
      myModelRef = WeakReference<MergeModelBase<*>?>(model)
    }

    override fun undo() {
      val model = myModelRef.get()
      if (model != null && myUndo) restoreStates(model)
    }

    override fun redo() {
      val model = myModelRef.get()
      if (model != null && !myUndo) restoreStates(model)
    }

    fun restoreStates(model: MergeModelBase<*>) {
      if (model.isDisposed) return
      if (model.changesCount == 0) return

      model.enterBulkChangeUpdateBlock()
      try {
        for (state in myStates) {
          model.restoreChangeState(state)
          model.invalidateHighlighters(state.myIndex)
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
  fun replaceChange(index: Int, newContent: MutableList<out CharSequence?>) {
    LOG.assertTrue(this.isInsideCommand)
    val outputStartLine = getLineStart(index)
    val outputEndLine = getLineEnd(index)

    DiffUtil.applyModification(myDocument, outputStartLine, outputEndLine, newContent)

    if (outputStartLine == outputEndLine) { // onBeforeDocumentChange() should process other cases correctly
      val newOutputEndLine = outputStartLine + newContent.size
      moveChangesAfterInsertion(index, outputStartLine, newOutputEndLine)
    }
  }

  @RequiresWriteLock
  fun appendChange(index: Int, newContent: MutableList<out CharSequence?>) {
    LOG.assertTrue(this.isInsideCommand)
    val outputStartLine = getLineStart(index)
    val outputEndLine = getLineEnd(index)

    DiffUtil.applyModification(myDocument, outputEndLine, outputEndLine, newContent)

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
    LOG.assertTrue(this.isInsideCommand)

    if (getLineStart(index) != newOutputStartLine ||
        getLineEnd(index) != newOutputEndLine
    ) {
      setLineStart(index, newOutputStartLine)
      setLineEnd(index, newOutputEndLine)
      invalidateHighlighters(index)
    }

    var beforeChange = true
    for (otherIndex in 0..<this.changesCount) {
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
        invalidateHighlighters(otherIndex)
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
    while (directArrayIndex < directChanges.size && otherIndex < this.changesCount) {
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
  open class State(@JvmField val myIndex: Int, val myStartLine: Int, val myEndLine: Int)
  companion object {
    private val LOG = Logger.getInstance(MergeModelBase::class.java)
  }
}
