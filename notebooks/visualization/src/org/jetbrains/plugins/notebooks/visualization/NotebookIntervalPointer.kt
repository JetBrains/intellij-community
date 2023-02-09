package org.jetbrains.plugins.notebooks.visualization

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
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

  /**
   * listener shouldn't throw exceptions
   */
  interface ChangeListener : EventListener {
    fun onUpdated(event: NotebookIntervalPointersEvent)
  }

  val changeListeners: EventDispatcher<ChangeListener>

  companion object {
    internal val key = Key.create<NotebookIntervalPointerFactory>(NotebookIntervalPointerFactory::class.java.name)

    fun get(editor: Editor): NotebookIntervalPointerFactory =
      getOrNull(editor)!!

    fun get(project: Project, document: Document): NotebookIntervalPointerFactory =
      getOrNull(project, document)!!

    fun getOrNull(editor: Editor): NotebookIntervalPointerFactory? {
      val project = editor.project ?: return null
      return getOrNull(project, editor.document)
    }

    fun getOrNull(project: Project, document: Document): NotebookIntervalPointerFactory? {
      return key.get(document) ?: tryInstall(project, document)
    }

    private fun tryInstall(project: Project, document: Document): NotebookIntervalPointerFactory? =
      getLanguage(project, document)
        ?.let { NotebookIntervalPointerFactoryProvider.forLanguage(it) }
        ?.create(project, document)
        ?.also { key.set(document, it) }
  }

  sealed interface Change

  /** invalidate pointer to interval, create new pointer */
  data class Invalidate(val interval: NotebookCellLines.Interval) : Change

  /** swap two pointers */
  data class Swap(val firstOrdinal: Int, val secondOrdinal: Int) : Change
}
