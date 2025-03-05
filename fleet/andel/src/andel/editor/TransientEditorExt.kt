// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.editor

import andel.operation.Operation
import andel.text.CharOffset
import andel.text.TextRange
import andel.text.charSequence
import andel.text.replaceOperation

fun TransientEditor.moveCaret(absoluteOffset: Long): Unit =
  moveCarets(listOf(currentCaret.copy(CaretPosition(absoluteOffset))))

fun TransientEditor.moveCaretRelatively(relativeOffset: Int, expandSelection: Boolean = false): Unit =
  moveCaretRelatively(currentCaret, relativeOffset.toLong(), expandSelection)

fun TransientEditor.insert(offset: Long, text: String): Unit =
  document.edit(
    Operation.insertAt(
      offset, text, document.text.charCount.toLong()
    )
  )

fun TransientEditor.insertBeforeCaret(toInsert: Any): Unit = insertBeforeCarets(mapOf(currentCaret.caretId to toInsert.toString()))

fun TransientEditor.insertAfterCaret(toInsert: Any): Unit = insertAfterCarets(mapOf(currentCaret.caretId to toInsert.toString()))

fun TransientEditor.deleteBeforeCaret(delete: Long): Unit = deleteBeforeCarets(mapOf(currentCaret.caretId to delete))

fun TransientEditor.deleteAfterCaret(delete: Long): Unit = deleteAfterCarets(mapOf(currentCaret.caretId to delete))

fun TransientEditor.delete(range: TextRange): Unit = delete(range.start, range.end)

fun TransientEditor.delete(start: CharOffset, end: CharOffset) {
  val textToDelete = document.text.view().charSequence(start, end)
  document.edit(Operation.deleteAt(start, textToDelete.toString(), document.text.charCount.toLong()))
}

fun TransientEditor.replace(from: Int, to: Int, replacement: String): Unit {
  val op = document.text.view().replaceOperation(from, to, replacement)
  document.edit(op)
}

fun TransientEditor.handleDefaultTyping(input: String) {
  if (currentCaret.hasSelection())
    delete(currentCaret.selection)
  insertBeforeCaret(input)
}