package org.jetbrains.plugins.notebooks.editor

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key

/**
 * Pointer becomes invalid when code cell is edited
 * Invalid pointer returns null
 */
interface NotebookIntervalPointer {
  fun get(): NotebookCellLines.Interval?
}

private val key = Key.create<NotebookIntervalPointerFactory>(NotebookIntervalPointerFactory::class.java.name)

interface NotebookIntervalPointerFactory {
  /** interval should be valid, return pointer to it */
  fun create(interval: NotebookCellLines.Interval): NotebookIntervalPointer

  companion object {
    fun get(editor: Editor): NotebookIntervalPointerFactory =
      key.get(editor) ?: install(editor)

    private fun install(editor: Editor): NotebookIntervalPointerFactory =
      NotebookIntervalPointerFactoryImpl(NotebookCellLines.get(editor)).also {
        key.set(editor, it)
      }
  }
}
