package org.jetbrains.plugins.notebooks.visualization

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.undo.BasicUndoableAction
import com.intellij.openapi.command.undo.DocumentReference
import com.intellij.openapi.command.undo.DocumentReferenceManager
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.util.EventDispatcher
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.notebooks.visualization.NotebookIntervalPointersEvent.*

class NotebookIntervalPointerFactoryImplProvider : NotebookIntervalPointerFactoryProvider {
  override fun create(editor: Editor): NotebookIntervalPointerFactory =
    NotebookIntervalPointerFactoryImpl(NotebookCellLines.get(editor),
                                       DocumentReferenceManager.getInstance().create(editor.document),
                                       editor.project?.let(UndoManager::getInstance))
}


private class NotebookIntervalPointerImpl(var interval: NotebookCellLines.Interval?) : NotebookIntervalPointer {
  override fun get(): NotebookCellLines.Interval? {
    ApplicationManager.getApplication().assertReadAccessAllowed()
    return interval
  }

  override fun toString(): String = "NotebookIntervalPointerImpl($interval)"
}


private typealias NotebookIntervalPointersEventChanges = ArrayList<Change>


private sealed interface ChangesContext

private data class DocumentChangedContext(var redoContext: RedoContext? = null) : ChangesContext
private data class UndoContext(val changes: List<Change>) : ChangesContext
private data class RedoContext(val changes: List<Change>) : ChangesContext

/**
 * One unique NotebookIntervalPointer exists for each current interval. You can use NotebookIntervalPointer as map key.
 * [NotebookIntervalPointerFactoryImpl] automatically supports undo/redo for [documentChanged] and [modifyPointers] calls.
 *
 * During undo or redo operations old intervals are restored.
 * For example, you can save pointer anywhere, remove interval, undo removal and pointer instance will contain interval again.
 * You can store interval-related data into WeakHashMap<NotebookIntervalPointer, Data> and this data will outlive undo/redo actions.
 */
class NotebookIntervalPointerFactoryImpl(private val notebookCellLines: NotebookCellLines,
                                         private val documentReference: DocumentReference,
                                         private val undoManager: UndoManager?) : NotebookIntervalPointerFactory, NotebookCellLines.IntervalListener {
  private val pointers = ArrayList<NotebookIntervalPointerImpl>()
  private var changesContext: ChangesContext? = null
  override val changeListeners: EventDispatcher<NotebookIntervalPointerFactory.ChangeListener> =
    EventDispatcher.create(NotebookIntervalPointerFactory.ChangeListener::class.java)

  init {
    pointers.addAll(notebookCellLines.intervals.asSequence().map { NotebookIntervalPointerImpl(it) })
    notebookCellLines.intervalListeners.addListener(this)
  }

  override fun create(interval: NotebookCellLines.Interval): NotebookIntervalPointer {
    ApplicationManager.getApplication().assertReadAccessAllowed()
    return pointers[interval.ordinal].also {
      require(it.interval == interval)
    }
  }

  override fun modifyPointers(changes: Iterable<NotebookIntervalPointerFactory.Change>) {
    ApplicationManager.getApplication()?.assertWriteAccessAllowed()

    val eventChanges = NotebookIntervalPointersEventChanges()
    applyChanges(changes, eventChanges)

    val pointerEvent = NotebookIntervalPointersEvent(eventChanges, cellLinesEvent = null, EventSource.ACTION)

    undoManager?.undoableActionPerformed(object : BasicUndoableAction(documentReference) {
      override fun undo() {
        val invertedChanges = invertChanges(eventChanges)
        updatePointersByChanges(invertedChanges)
        changeListeners.multicaster.onUpdated(
          NotebookIntervalPointersEvent(invertedChanges, cellLinesEvent = null, EventSource.UNDO_ACTION))
      }

      override fun redo() {
        updatePointersByChanges(eventChanges)
        changeListeners.multicaster.onUpdated(
          NotebookIntervalPointersEvent(eventChanges, cellLinesEvent = null, EventSource.REDO_ACTION))
      }
    })

    changeListeners.multicaster.onUpdated(pointerEvent)
  }

  override fun documentChanged(event: NotebookCellLinesEvent) {
    try {
      val pointersEvent = when (val context = changesContext) {
        is DocumentChangedContext -> documentChangedByAction(event, context)
        is UndoContext -> documentChangedByUndo(event, context)
        is RedoContext -> documentChangedByRedo(event, context)
        null -> documentChangedByAction(event, null) // changesContext is null if undo manager is unavailable
      }
      changeListeners.multicaster.onUpdated(pointersEvent)
    }
    catch (ex: Exception) {
      thisLogger().error(ex)
      // DS-3893 consume exception and log it, actions changing document should work as usual
    }
    finally {
      changesContext = null
    }
  }

  override fun beforeDocumentChange(event: NotebookCellLinesEventBeforeChange) {
    if (undoManager == null || undoManager.isUndoOrRedoInProgress) return
    val context = DocumentChangedContext()
    try {
      undoManager.undoableActionPerformed(object : BasicUndoableAction() {
        override fun undo() {}

        override fun redo() {
          changesContext = context.redoContext
        }
      })
      changesContext = context
    } catch (ex: Exception) {
      thisLogger().error(ex)
      // DS-3893 consume exception, don't prevent document updating
    }
  }

  private fun documentChangedByAction(event: NotebookCellLinesEvent,
                                      documentChangedContext: DocumentChangedContext?): NotebookIntervalPointersEvent {
    val eventChanges = NotebookIntervalPointersEventChanges()

    updateChangedIntervals(event, eventChanges)
    updateShiftedIntervals(event)

    undoManager?.undoableActionPerformed(object : BasicUndoableAction(documentReference) {
      override fun undo() {
        changesContext = UndoContext(eventChanges)
      }

      override fun redo() {}
    })

    documentChangedContext?.let {
      it.redoContext = RedoContext(eventChanges)
    }

    return NotebookIntervalPointersEvent(eventChanges, event, EventSource.ACTION)
  }

  private fun documentChangedByUndo(event: NotebookCellLinesEvent, context: UndoContext): NotebookIntervalPointersEvent {
    val invertedChanges = invertChanges(context.changes)
    updatePointersByChanges(invertedChanges)
    updateShiftedIntervals(event)
    return NotebookIntervalPointersEvent(invertedChanges, event, EventSource.UNDO_ACTION)
  }

  private fun documentChangedByRedo(event: NotebookCellLinesEvent, context: RedoContext): NotebookIntervalPointersEvent {
    updatePointersByChanges(context.changes)
    updateShiftedIntervals(event)
    return NotebookIntervalPointersEvent(context.changes, event, EventSource.REDO_ACTION)
  }

  private fun updatePointersByChanges(changes: List<Change>) {
    for (change in changes) {
      when (change) {
        is OnEdited -> (change.pointer as NotebookIntervalPointerImpl).interval = change.intervalAfter
        is OnInserted -> {
          for (p in change.subsequentPointers) {
            (p.pointer as NotebookIntervalPointerImpl).interval = p.interval
          }
          pointers.addAll(change.ordinals.first, change.subsequentPointers.map { it.pointer as NotebookIntervalPointerImpl })
        }
        is OnRemoved -> {
          for (p in change.subsequentPointers.asReversed()) {
            pointers.removeAt(p.interval.ordinal)
            (p.pointer as NotebookIntervalPointerImpl).interval = null
          }
        }
        is OnSwapped -> {
          trySwapPointers(null, NotebookIntervalPointerFactory.Swap(change.firstOrdinal, change.secondOrdinal))
        }
      }
    }
  }

  private fun makeSnapshot(interval: NotebookCellLines.Interval) =
    PointerSnapshot(pointers[interval.ordinal], interval)

  private fun updateChangedIntervals(e: NotebookCellLinesEvent, eventChanges: NotebookIntervalPointersEventChanges) {
    when {
      !e.isIntervalsChanged() -> {
        // content edited without affecting intervals values
        for (editedInterval in LinkedHashSet(e.oldAffectedIntervals) + e.newAffectedIntervals) {
          eventChanges.add(OnEdited(pointers[editedInterval.ordinal], editedInterval, editedInterval))
        }
      }
      e.oldIntervals.size == 1 && e.newIntervals.size == 1 && e.oldIntervals.first().type == e.newIntervals.first().type -> {
        // only one interval changed size
        for (editedInterval in e.newAffectedIntervals) {
          val ptr = pointers[editedInterval.ordinal]
          eventChanges.add(OnEdited(ptr, ptr.interval!!, editedInterval))
        }
        if (e.newIntervals.first() !in e.newAffectedIntervals) {
          val ptr = pointers[e.newIntervals.first().ordinal]
          eventChanges.add(OnEdited(ptr, ptr.interval!!, e.newIntervals.first()))
        }

        pointers[e.newIntervals.first().ordinal].interval = e.newIntervals.first()
      }
      else -> {
        if (e.oldIntervals.isNotEmpty()) {
          eventChanges.add(OnRemoved(e.oldIntervals.map(::makeSnapshot)))

          for (old in e.oldIntervals.asReversed()) {
            pointers[old.ordinal].interval = null
            pointers.removeAt(old.ordinal)
          }
        }

        if (e.newIntervals.isNotEmpty()) {
          pointers.addAll(e.newIntervals.first().ordinal, e.newIntervals.map { NotebookIntervalPointerImpl(it) })
          eventChanges.add(OnInserted(e.newIntervals.map(::makeSnapshot)))
        }

        for (interval in e.newAffectedIntervals - e.newIntervals.toSet()) {
          val ptr = pointers[interval.ordinal]
          eventChanges.add(OnEdited(ptr, ptr.interval!!, interval))
        }
      }
    }
  }

  private fun updateShiftedIntervals(event: NotebookCellLinesEvent) {
    val invalidPointersStart =
      event.newIntervals.firstOrNull()?.let { it.ordinal + event.newIntervals.size }
      ?: event.oldIntervals.firstOrNull()?.ordinal
      ?: pointers.size

    for (i in invalidPointersStart until pointers.size) {
      pointers[i].interval = notebookCellLines.intervals[i]
    }
  }

  private fun applyChanges(changes: Iterable<NotebookIntervalPointerFactory.Change>, eventChanges: NotebookIntervalPointersEventChanges){
    for (hint in changes) {
      when (hint) {
        is NotebookIntervalPointerFactory.Invalidate -> {
          val ptr = create(hint.interval) as NotebookIntervalPointerImpl
          invalidatePointer(eventChanges, ptr)
        }
        is NotebookIntervalPointerFactory.Swap ->
          trySwapPointers(eventChanges, hint)
      }
    }
  }

  private fun invalidatePointer(eventChanges: NotebookIntervalPointersEventChanges,
                                ptr: NotebookIntervalPointerImpl) {
    val interval = ptr.interval
    if (interval == null) return

    val newPtr = NotebookIntervalPointerImpl(interval)
    pointers[interval.ordinal] = newPtr
    ptr.interval = null

    eventChanges.add(OnRemoved(listOf(PointerSnapshot(ptr, interval))))
    eventChanges.add(OnInserted(listOf(PointerSnapshot(newPtr, interval))))
  }

  private fun trySwapPointers(eventChanges: NotebookIntervalPointersEventChanges?,
                              hint: NotebookIntervalPointerFactory.Swap) {
    val firstPtr = pointers.getOrNull(hint.firstOrdinal)
    val secondPtr = pointers.getOrNull(hint.secondOrdinal)

    if (firstPtr == null || secondPtr == null) {
      thisLogger().error("cannot swap invalid NotebookIntervalPointers: ${hint.firstOrdinal} and ${hint.secondOrdinal}")
      return
    }

    if (hint.firstOrdinal == hint.secondOrdinal) return // nothing to do

    val interval = firstPtr.interval!!
    firstPtr.interval = secondPtr.interval
    secondPtr.interval = interval

    pointers[hint.firstOrdinal] = secondPtr
    pointers[hint.secondOrdinal] = firstPtr

    eventChanges?.add(OnSwapped(PointerSnapshot(firstPtr, firstPtr.interval!!),
                                PointerSnapshot(secondPtr, secondPtr.interval!!)))
  }

  private fun invertChanges(changes: List<Change>): List<Change> =
    changes.asReversed().map(::invertChange)

  private fun invertChange(change: Change): Change =
    when (change) {
      is OnEdited -> change.copy(intervalAfter = change.intervalBefore, intervalBefore = change.intervalAfter)
      is OnInserted -> OnRemoved(change.subsequentPointers)
      is OnRemoved -> OnInserted(change.subsequentPointers)
      is OnSwapped -> OnSwapped(first = PointerSnapshot(change.first.pointer, change.second.interval),
                                second = PointerSnapshot(change.second.pointer, change.first.interval))
    }

  @TestOnly
  fun pointersCount(): Int = pointers.size
}
