package org.jetbrains.plugins.notebooks.editor

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key

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
  fun <T> withHints(hints: Iterable<Hint>, modifyDocumentAction: () -> T): T

  companion object {
    fun get(editor: Editor): NotebookIntervalPointerFactory =
      getOrNull(editor)!!

    fun getOrNull(editor: Editor): NotebookIntervalPointerFactory? =
      key.get(editor) ?: tryInstall(editor)

    private fun tryInstall(editor: Editor): NotebookIntervalPointerFactory? =
      getLanguage(editor)
        ?.let { NotebookIntervalPointerFactoryProvider.forLanguage(it) }
        ?.create(editor)
        ?.also { key.set(editor, it) }
  }

  sealed interface Hint

  /** invalidate pointer, create new pointer for interval if necessary */
  data class Invalidate(val ptr: NotebookIntervalPointer) : Hint

  /** reuse old pointer instead of creating new, for example when moving interval */
  data class Reuse(val ptr: NotebookIntervalPointer, val ordinalAfterChange: Int) : Hint
}

fun <T> NotebookIntervalPointerFactory?.invalidate(cell: NotebookCellLines.Interval, action: () -> T): T =
  if (this == null) {
    action()
  }
  else {
    withHints(listOf(NotebookIntervalPointerFactory.Invalidate(create(cell)))) {
      action()
    }
  }

/**
 * input pairs - current interval and it's ordinal after document change
 */
fun <T> NotebookIntervalPointerFactory?.moveIntervals(newPositions: List<Pair<NotebookCellLines.Interval, Int>>, action: () -> T): T =
  if (this == null) {
    action()
  }
  else {
    withHints(newPositions.map { (interval, newOrdinal) -> NotebookIntervalPointerFactory.Reuse(create(interval), newOrdinal) }) {
      action()
    }
  }
