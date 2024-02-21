package org.jetbrains.plugins.notebooks.visualization

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.undo.BasicUndoableAction
import com.intellij.openapi.command.undo.DocumentReference
import com.intellij.openapi.command.undo.DocumentReferenceManager
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.EventDispatcher
import com.intellij.util.concurrency.ThreadingAssertions
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.notebooks.visualization.NotebookIntervalPointersEvent.*

class NotebookIntervalPointerFactoryImplProvider : NotebookIntervalPointerFactoryProvider {
  override fun create(project: Project, document: Document): NotebookIntervalPointerFactory {
    val notebookCellLines = NotebookCellLines.get(document)
    val factory = NotebookIntervalPointerFactoryImpl(notebookCellLines,
                                                     DocumentReferenceManager.getInstance().create(document),
                                                     UndoManager.getInstance(project),
                                                     project)

    notebookCellLines.intervalListeners.addListener(factory)
    Disposer.register(project) {
      notebookCellLines.intervalListeners.removeListener(factory)
      NotebookIntervalPointerFactory.key.set(document, null)
    }

    return factory
  }
}


private class NotebookIntervalPointerImpl(@Volatile var interval: NotebookCellLines.Interval?) : NotebookIntervalPointer {
  override fun get(): NotebookCellLines.Interval? = interval

  override fun toString(): String = "NotebookIntervalPointerImpl($interval)"
}


private typealias NotebookIntervalPointersEventChanges = ArrayList<Change>

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
                                         undoManager: UndoManager?,
                                         private val project: Project) : NotebookIntervalPointerFactory, NotebookCellLines.IntervalListener {
  private val pointers = ArrayList<NotebookIntervalPointerImpl>()
  private var postponedEvent: NotebookIntervalPointersEvent? = null
  override val changeListeners: EventDispatcher<NotebookIntervalPointerFactory.ChangeListener> =
    EventDispatcher.create(NotebookIntervalPointerFactory.ChangeListener::class.java)

  init {
    pointers.addAll(notebookCellLines.intervals.asSequence().map { NotebookIntervalPointerImpl(it) })
  }

  private val validUndoManager: UndoManager? = undoManager
    get() = field?.takeIf { !project.isDisposed }

  override fun create(interval: NotebookCellLines.Interval): NotebookIntervalPointer {
    ThreadingAssertions.assertReadAccess()
    return pointers[interval.ordinal].also {
      require(it.interval == interval)
    }
  }

  override fun modifyPointers(changes: Iterable<NotebookIntervalPointerFactory.Change>) {
    ThreadingAssertions.assertWriteAccess()

    val eventChanges = NotebookIntervalPointersEventChanges()
    applyChanges(changes, eventChanges)

    val pointerEvent = NotebookIntervalPointersEvent(eventChanges, cellLinesEvent = null, EventSource.ACTION)

    validUndoManager?.undoableActionPerformed(object : BasicUndoableAction(documentReference) {
      override fun undo() {
        ThreadingAssertions.assertWriteAccess()
        val invertedChanges = invertChanges(eventChanges)
        updatePointersByChanges(invertedChanges)
        onUpdated(NotebookIntervalPointersEvent(invertedChanges, cellLinesEvent = null, EventSource.UNDO_ACTION))
      }

      override fun redo() {
        ThreadingAssertions.assertWriteAccess()
        updatePointersByChanges(eventChanges)
        onUpdated(NotebookIntervalPointersEvent(eventChanges, cellLinesEvent = null, EventSource.REDO_ACTION))
      }
    })

    onUpdated(pointerEvent)
  }

  override fun documentChanged(event: NotebookCellLinesEvent) {
    ThreadingAssertions.assertWriteAccess()
    try {
      if (validUndoManager?.isUndoOrRedoInProgress != true) {
        documentChangedByAction(event)
      } else {
        val e = postponedEvent
        if (e != null) {
          onUpdated(e)
        }
      }
    }
    catch (ex: Exception) {
      thisLogger().error(ex)
      // DS-3893 consume exception and log it, actions changing document should work as usual
    } finally {
      postponedEvent = null
    }
  }

  private fun documentChangedByAction(event: NotebookCellLinesEvent) {
    val eventChanges = updateChangedIntervals(event)
    val shiftChanges = updateShiftedIntervals(event)

    validUndoManager?.undoableActionPerformed(object : BasicUndoableAction(documentReference) {
      override fun undo() {
        ThreadingAssertions.assertWriteAccess()
        updatePointersByChanges(invertChanges(shiftChanges))
        val invertChanges = invertChanges(eventChanges)
        updatePointersByChanges(invertChanges)
        onUpdated(NotebookIntervalPointersEvent(invertChanges, event, EventSource.UNDO_ACTION))
      }

      override fun redo() {
        ThreadingAssertions.assertWriteAccess()
        updatePointersByChanges(eventChanges)
        updatePointersByChanges(shiftChanges)
        postponedEvent = NotebookIntervalPointersEvent(eventChanges, event, EventSource.REDO_ACTION)
      }
    })
    onUpdated(NotebookIntervalPointersEvent(eventChanges, event, EventSource.ACTION))
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

  private fun hasSingleIntervalsWithSameTypeAndLanguage(oldIntervals: List<NotebookCellLines.Interval>,
                                                        newIntervals: List<NotebookCellLines.Interval>): Boolean {
    val old = oldIntervals.singleOrNull() ?: return false
    val new = newIntervals.singleOrNull() ?: return false
    return old.type == new.type && old.language == new.language
  }

  private fun updateChangedIntervals(e: NotebookCellLinesEvent): NotebookIntervalPointersEventChanges {
    val eventChanges = NotebookIntervalPointersEventChanges()
    when {
      !e.isIntervalsChanged() -> {
        // content edited without affecting intervals values
        for (editedInterval in LinkedHashSet(e.oldAffectedIntervals) + e.newAffectedIntervals) {
          eventChanges.add(OnEdited(pointers[editedInterval.ordinal], editedInterval, editedInterval))
        }
      }
      hasSingleIntervalsWithSameTypeAndLanguage(e.oldIntervals, e.newIntervals) -> {
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
    return eventChanges
  }

  private fun updateShiftedIntervals(event: NotebookCellLinesEvent): NotebookIntervalPointersEventChanges {
    val invalidPointersStart =
      event.newIntervals.firstOrNull()?.let { it.ordinal + event.newIntervals.size }
      ?: event.oldIntervals.firstOrNull()?.ordinal
      ?: pointers.size

    val eventChanges = NotebookIntervalPointersEventChanges()
    val intervals = notebookCellLines.intervals
    for (i in invalidPointersStart until pointers.size) {
      val ptr = pointers[i]
      val intervalBefore = ptr.interval!!
      val intervalAfter = intervals[i]
      ptr.interval = intervals[i]
      eventChanges.add(OnEdited(ptr, intervalBefore, intervalAfter))
    }
    return eventChanges
  }

  private fun applyChanges(changes: Iterable<NotebookIntervalPointerFactory.Change>, eventChanges: NotebookIntervalPointersEventChanges) {
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

  private fun onUpdated(event: NotebookIntervalPointersEvent) {
    safelyUpdate(changeListeners.multicaster, event)
    safelyUpdate(ApplicationManager.getApplication().messageBus.syncPublisher(NotebookIntervalPointerFactory.ChangeListener.TOPIC), event)
  }

  private fun safelyUpdate(listener: NotebookIntervalPointerFactory.ChangeListener, event: NotebookIntervalPointersEvent) {
    try {
      listener.onUpdated(event)
    }
    catch (t: Throwable) {
      thisLogger().error("$listener shouldn't throw exceptions", t)
    }
  }

  @TestOnly
  fun pointersCount(): Int = pointers.size
}
