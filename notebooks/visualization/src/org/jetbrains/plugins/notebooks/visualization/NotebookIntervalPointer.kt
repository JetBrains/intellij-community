package org.jetbrains.plugins.notebooks.visualization

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key
import com.intellij.util.EventDispatcher
import java.util.*

/**
 * Pointer becomes invalid when code cell is removed
 * Invalid pointer returns null
 */
interface NotebookIntervalPointer {
  fun get(): NotebookCellLines.Interval?
}

private val key = Key.create<NotebookIntervalPointerFactory>(NotebookIntervalPointerFactory::class.java.name)

interface NotebookIntervalPointerFactory {
  /** interval should be valid, return pointer to it */
  fun create(interval: NotebookCellLines.Interval): NotebookIntervalPointer

  /**
   * if action changes document, hints applied instantly at document change
   * if doesn't - hints applied after action
   * hint should contain pointers created by this factory
   */
  fun <T> modifyingPointers(changes: Iterable<Change>, modifyDocumentAction: () -> T): T

  fun invalidateCell(cell: NotebookCellLines.Interval) {
    modifyingPointers(listOf(Invalidate(create(cell)))) {}
  }

  fun swapCells(swapped: List<Swap>) {
    modifyingPointers(swapped) {}
  }

  interface ChangeListener : EventListener {
    fun onUpdated(event: NotebookIntervalPointersEvent) {
      for(change in event.changes) {
        when (change) {
          is NotebookIntervalPointersEvent.OnEdited -> onEdited(change.ordinal)
          is NotebookIntervalPointersEvent.OnInserted -> onInserted(change.ordinals)
          is NotebookIntervalPointersEvent.OnRemoved -> onRemoved(change.ordinals)
          is NotebookIntervalPointersEvent.OnSwapped -> onSwapped(change.firstOrdinal, change.secondOrdinal)
        }
      }
    }

    fun onInserted(ordinals: IntRange)

    fun onEdited(ordinal: Int)

    fun onRemoved(ordinals: IntRange)

    /** [firstOrdinal] and [secondOrdinal] are never equal */
    fun onSwapped(firstOrdinal: Int, secondOrdinal: Int)
  }

  val changeListeners: EventDispatcher<ChangeListener>

  companion object {
    fun get(editor: Editor): NotebookIntervalPointerFactory =
      getOrNull(editor)!!

    fun getOrNull(editor: Editor): NotebookIntervalPointerFactory? =
      key.get(editor.document) ?: tryInstall(editor)

    private fun tryInstall(editor: Editor): NotebookIntervalPointerFactory? =
      getLanguage(editor)
        ?.let { NotebookIntervalPointerFactoryProvider.forLanguage(it) }
        ?.create(editor)
        ?.also { key.set(editor.document, it) }
  }

  sealed interface Change

  /** invalidate pointer, create new pointer for interval if necessary */
  data class Invalidate(val ptr: NotebookIntervalPointer) : Change

  /** swap two pointers */
  data class Swap(val firstOrdinal: Int, val secondOrdinal: Int) : Change
}

fun <T> NotebookIntervalPointerFactory?.invalidatingCell(cell: NotebookCellLines.Interval, action: () -> T): T =
  if (this == null) {
    action()
  }
  else {
    modifyingPointers(listOf(NotebookIntervalPointerFactory.Invalidate(create(cell)))) {
      action()
    }
  }

fun <T> NotebookIntervalPointerFactory?.swappingCells(swapedCells: List<NotebookIntervalPointerFactory.Swap>, action: () -> T): T =
  if (this == null) {
    action()
  }
  else {
    modifyingPointers(swapedCells) {
      action()
    }
  }
