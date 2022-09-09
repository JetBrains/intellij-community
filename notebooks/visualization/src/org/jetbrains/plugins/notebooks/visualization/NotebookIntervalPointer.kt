package org.jetbrains.plugins.notebooks.visualization

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key
import com.intellij.util.EventDispatcher
import java.util.*

/**
 * Pointer becomes invalid when code cell is removed.
 * It may become valid again when action is undone or redone.
 * Invalid pointer returns null.
 */
interface NotebookIntervalPointer {
  /** should be called in read-action */
  fun get(): NotebookCellLines.Interval?
}

private val key = Key.create<NotebookIntervalPointerFactory>(NotebookIntervalPointerFactory::class.java.name)

interface NotebookIntervalPointerFactory {
  /**
   * Interval should be valid, return pointer to it.
   * Should be called in read-action.
   */
  fun create(interval: NotebookCellLines.Interval): NotebookIntervalPointer

  /**
   * Can be called only inside write-action.
   * Undo and redo will be added automatically.
   */
  fun modifyPointers(changes: Iterable<Change>)

  interface ChangeListener : EventListener {
    fun onUpdated(event: NotebookIntervalPointersEvent)
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

  /** invalidate pointer to interval, create new pointer */
  data class Invalidate(val interval: NotebookCellLines.Interval) : Change

  /** swap two pointers */
  data class Swap(val firstOrdinal: Int, val secondOrdinal: Int) : Change
}
