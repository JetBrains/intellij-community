// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.editor

import andel.operation.Op
import andel.operation.Operation
import andel.text.charSequence
import andel.text.line
import andel.text.lineEndOffset
import fleet.util.normalizeLineEndings
import kotlin.collections.ArrayList

val MutableEditor.carets: List<Caret> get() = multiCaret.carets
val MutableEditor.primaryCaret: Caret get() = multiCaret.primaryCaret
fun MutableEditor.addCaret(position: CaretPosition): Caret = with(multiCaret) {
  addCarets(listOf(position))
  requireNotNull(multiCaret.carets.find { it.position == position })
}
fun MutableEditor.addCarets(positions: List<CaretPosition>) { multiCaret.addCarets(positions) }
fun MutableEditor.removeCaret(caret: Caret) = multiCaret.removeCarets(listOf(caret))
fun MutableEditor.removeCarets(carets: Collection<Caret>) = multiCaret.removeCarets(carets)
fun MutableEditor.moveCarets(moves: List<Caret>) = multiCaret.moveCarets(moves)
fun MutableEditor.moveCaretRelatively(caret: Caret, relativeOffset: Long, expandSelection: Boolean = false) {
  val position = caret.position
  moveCarets(listOf(caret.move(position.move(position.offset + relativeOffset, expandSelection))))
}

fun MutableEditor.moveCaretRelatively(caret: Caret, relativeOffset: Int, expandSelection: Boolean = false) =
  moveCaretRelatively(caret, relativeOffset.toLong(), expandSelection)

fun Editor.deleteBeforeCaretsOperation(deletions: Map<CaretId, Long>): Operation {
  val ops: MutableList<Op> = ArrayList()
  var lastOffset: Long = 0
  val sortedDeletions = deletions.map { multiCaret.multiCaretData.resolveAnchor(it.key) to it.value }.toList().sortedBy { it.first }
  for (deletion in sortedDeletions) {
    val to = deletion.first
    val from = (to - deletion.second).coerceAtLeast(0)
    ops.add(Op.Retain(from - lastOffset))
    ops.add(Op.Replace(document.text.view().string(from.toInt(), to.toInt()), ""))
    lastOffset = to
  }
  ops.add(Op.Retain(document.text.charCount.toLong() - lastOffset))
  return Operation(ops)
}

fun Editor.deleteAfterCaretOperation(deletions: Map<CaretId, Long>): Operation {
  val ops: MutableList<Op> = ArrayList()
  var lastOffset: Long = 0
  val sortedDeletions = deletions.map { multiCaret.multiCaretData.resolveAnchor(it.key) to it.value }.toList().sortedBy { it.first }
  for (deletion in sortedDeletions) {
    val from = deletion.first
    val to = (from + deletion.second).coerceAtMost(document.text.charCount.toLong())
    ops.add(Op.Retain(from - lastOffset))
    ops.add(Op.Replace(document.text.view().string(from.toInt(), to.toInt()), ""))
    lastOffset = to
  }
  ops.add(Op.Retain(document.text.charCount.toLong() - lastOffset))
  return Operation(ops)
}

fun Editor.insertAtCaretsOperation(insertions: Map<CaretId, String>): Operation {
  val sortedInsertions = insertions
    .map { (anchorId, str) ->
      multiCaret.multiCaretData.resolveAnchor(anchorId) to str
    }
    .sortedBy { it.first }
    .toList()
  var lastOffset: Long = 0
  val ops = ArrayList<Op>()
  for (insertion in sortedInsertions) {
    val text = insertion.second
    if (oneLine) {
      val newlineMatch = newlineRegex.matchEntire(text)
      require(newlineMatch == null) {
        val escapedMatch = newlineMatch!!.value
          .replace("\n", "\\n")
          .replace("\r", "\\r")
        "Attempted to insert a newline character into a oneLine Editor: \"$escapedMatch\""
      }
    }
    ops.add(Op.Retain(insertion.first - lastOffset))
    ops.add(Op.Replace("", text))
    lastOffset = insertion.first
  }
  ops.add(Op.Retain(document.text.charCount.toLong() - lastOffset))
  return Operation(ops)
}

private val newlineRegex = Regex("[\\n\\r]+")

fun MutableEditor.deleteBeforeCarets(deletions: Map<CaretId, Long>) {
  document.edit(deleteBeforeCaretsOperation(deletions))
}

fun MutableEditor.deleteAfterCarets(deletions: Map<CaretId, Long>) {
  document.edit(deleteAfterCaretOperation(deletions))
}

fun MutableEditor.insertBeforeCarets(insertions: Map<CaretId, String>) {
  val operation = insertAtCaretsOperation(insertions)
  document.edit(operation)
  moveAllCarets { caret ->
    val insertion = insertions[caret.caretId]
    if (insertion == null)
      caret
    else {
      val newOffset = caret.position.offset + insertion.length
      val selectionStart = if (caret.position.selectionStart == caret.position.offset) newOffset else caret.position.selectionStart
      val selectionEnd = if (caret.position.selectionEnd == caret.position.offset) newOffset else caret.position.selectionEnd
      caret.move(CaretPosition(newOffset, selectionStart, selectionEnd))
    }
  }
}

fun MutableEditor.insertAfterCarets(insertions: Map<CaretId, String>) {
  val operation = insertAtCaretsOperation(insertions)
  document.edit(operation)
}

fun MutableEditor.setCaretAtOffset(offset: Long, selectionStart: Long = offset, selectionEnd: Long = offset) {
  dropMultiCaret()
  moveAllCarets { caret ->
    caret.move(CaretPosition(offset, selectionStart, selectionEnd))
  }
}

fun MutableEditor.insertAtAllCarets(text: String) {
  insertBeforeCarets(carets.associate { it.caretId to text })
}

fun MutableEditor.replaceAtAllCarets(input: List<String>) {
  val ranges = if (input.size > 1 && carets.size > 1) {
    carets.mapIndexed { index, caret -> caret.selection to (input.getOrNull(index)?.normalizeLineEndings() ?: "") }
  } else {
    val s = input.joinToString("\n").normalizeLineEndings()
    carets.map { caret -> caret.selection to s }
  }
  document.replaceRanges(ranges)
  var acc = 0L
  moveCarets(carets.mapIndexed { i, caret ->
    val newOffset = acc + ranges[i].first.start + ranges[i].second.length.toLong()
    acc += ranges[i].second.length - ranges[i].first.length
    caret.move(CaretPosition(newOffset))
  })
}

fun MutableEditor.moveAllCarets(f: (Caret) -> Caret?) {
  val moves = mutableListOf<Caret>()
  val deletes = mutableListOf<Caret>()
  for (caret in carets) {
    when (val moved = f(caret)) {
      null -> deletes.add(caret)
      else -> moves.add(moved)
    }
  }
  command(EditorCommandType.NAVIGATION) {
    removeCarets(deletes)
    moveCarets(moves)
  }
}

fun MutableEditor.dropSelection(caret: Caret) {
  moveCarets(listOf(caret.move(caret.position.withoutSelection(),
                               vCol = caret.vCol)))
}

fun MutableEditor.dropAllSelections() {
  moveAllCarets { caret ->
    caret.move(caret.position.withoutSelection(),
               vCol = caret.vCol)
  }
}

fun MutableEditor.dropMultiCaret() {
  removeCarets(carets - multiCaret.primaryCaret)
}

fun MutableEditor.setCarets(carets: List<CaretPosition>) {
  require(carets.isNotEmpty())
  removeCarets(multiCaret.carets)
  addCarets(carets)
  multiCaret.primaryCaret = multiCaret.carets.first()
}

fun MutableEditor.addCaretPerSelectedLine() {
  val text = document.text.view()
  setCarets(carets.flatMap { caret ->
    val selectionStartLineNumber = text.lineAt(caret.selection.start.toInt())
    val selectionEndLineNumber = text.lineAt(caret.selection.end.toInt())
    (selectionStartLineNumber.line .. selectionEndLineNumber.line).map { line ->
      CaretPosition(text.lineEndOffset(line.line).toLong())
    }
  })
}

fun MutableEditor.clear() {
  val text = document.text.view().charSequence().toString()
  document.edit(Operation.deleteAt(0, text, text.length.toLong()))
}