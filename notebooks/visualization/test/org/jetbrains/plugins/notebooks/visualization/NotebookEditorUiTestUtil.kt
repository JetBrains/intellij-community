package org.jetbrains.plugins.notebooks.visualization

import com.intellij.openapi.editor.CaretState
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.SmartList
import java.util.regex.Pattern


private const val caretToken = "<caret>"
private const val primaryCaretToken = "<primaryCaret>"
private const val selectionBegin = "<selection>"
private const val selectionEnd = "</selection>"
private const val foldBegin = "<fold>"
private const val foldEnd = "</fold>"


data class CaretWithSelection(val caret: Int, val selection: TextRange, val isPrimary: Boolean = false) {
  constructor(caret: Int, isPrimary: Boolean = false) : this(caret, TextRange(caret, caret), isPrimary)
}

data class ExtractedInfo(val text: String, val carets: List<CaretWithSelection>, val folds: List<TextRange>) {
  fun setCaretsInEditor(editor: Editor) {
    require(carets.size <= editor.caretModel.maxCaretCount)

    editor.caretModel.setCaretsAndSelections(carets.filter { !it.isPrimary }.map { caret ->
      CaretState(editor.offsetToLogicalPosition(caret.caret),
        editor.offsetToLogicalPosition(caret.selection.startOffset),
        editor.offsetToLogicalPosition(caret.selection.endOffset))
    })
    carets.singleOrNull { it.isPrimary }?.let { c ->
      val caret = editor.caretModel.addCaret(editor.offsetToLogicalPosition(c.caret), true)!!
      caret.setSelection(c.selection.startOffset, c.selection.endOffset)
    }

    for ((editorCaret, caret) in editor.caretModel.allCarets.zip(carets)) {
      require(editorCaret.offset == caret.caret)
      val errorMsg = { "can't set selection ${caret} to ${editorCaret.offset}, may be folded region exists" }
      require(editorCaret.selectionStart == caret.selection.startOffset, errorMsg)
      require(editorCaret.selectionEnd == caret.selection.endOffset, errorMsg)
    }
  }

  fun setFoldingsInEditor(editor: Editor) {
    editor.foldingModel.runBatchFoldingOperation {
      for (foldRange in folds) {
        editor.foldingModel.addFoldRegion(foldRange.startOffset, foldRange.endOffset, "folded")?.also {
          it.isExpanded = false
        }
      }
    }
  }
}

fun extractTextAndCaretOffset(text: String): Pair<String, Int?> {
  val caretOffset = text.indexOf(caretToken)
  if (caretOffset != -1) {
    return Pair(text.substring(0, caretOffset) + text.substring(caretOffset + caretToken.length), caretOffset)
  }
  else {
    return Pair(text, null)
  }
}

fun extractCaretsAndFoldings(text: String): ExtractedInfo {
  val tagTokens = listOf(caretToken, primaryCaretToken, selectionBegin, selectionEnd, foldBegin, foldEnd)
  val (textWithoutTags, tags) = extractCarets(text, findAllTags(text, tagTokens))
  return ExtractedInfo(textWithoutTags, parseCaretsInfo(tags), parseFoldings(tags))
}

/* add caretToken into the text */
val Editor.prettyText: String
  get() = document
    .text
    .lineSequence()
    .withIndex()
    .fold(0 to StringBuilder()) { (offset, text), (lineNumber, line) ->
      val lineWithCaret =
        caretModel
          .logicalPosition
          .takeIf { it.line == lineNumber }
          ?.column
          ?.let { line.substring(0, it) + caretToken + line.substring(it) }
        ?: line
      text.append("\nLine ${lineNumber.toString().padStart(2)} Offset ${offset.toString().padStart(3)}: $lineWithCaret")
      offset + line.length + 1 to text
    }
    .second
    .toString()

fun edt(runnable: () -> Unit) {
  runInEdtAndWait(runnable)
}

val Editor.textWithCarets: String
  get() {
    val tags = extractCarets(this)
    return insertTags(document.text, tags)
  }

private fun extractCarets(editor: Editor): List<Pair<String, Int>> {
  val tags = SmartList<Pair<String, Int>>()

  for (caret in editor.caretModel.allCarets) {
    if (caret.hasSelection()) {
      tags.add(Pair(selectionBegin, caret.selectionStart))
      tags.add(Pair(caretToken, caret.offset))
      tags.add(Pair(selectionEnd, caret.selectionEnd))
    }
    else tags.add(Pair(caretToken, caret.offset))
  }

  return tags
}

private fun insertTags(text: String, orderedTags: List<Pair<String, Int>>): String {
  val result = StringBuilder()

  var end = 0
  for ((tagText, tagPos) in orderedTags) {
    result.append(text.subSequence(end, tagPos))
    result.append(tagText)
    end = tagPos
  }
  result.append(text.subSequence(end, text.length))

  return result.toString()
}

private fun findAllTags(text: String, tags: List<String>): List<TextRange> {
  val regex = tags.joinToString(separator = "|", prefix = "(", postfix = ")") { Pattern.quote(it) }
  val p = Pattern.compile(regex)
  val m = p.matcher(text)

  val tagsInText = SmartList<TextRange>()
  while (m.find()) {
    tagsInText.add(TextRange(m.start(), m.end()))
  }
  return tagsInText
}

private fun extractCarets(text: String, ranges: List<TextRange>): Pair<String, List<Pair<String, Int>>> {
  val textWithoutTags = StringBuilder()
  val tags = SmartList<Pair<String, Int>>()

  var start = 0
  for (range in ranges) {
    textWithoutTags.append(text.subSequence(start, range.startOffset))
    tags += range.substring(text) to textWithoutTags.length
    start = range.endOffset
  }
  textWithoutTags.append(text.subSequence(start, text.length))

  return Pair(textWithoutTags.toString(), tags)
}

private fun parseCaretsInfo(tags: List<Pair<String, Int>>): List<CaretWithSelection> {
  val caretsTags = tags.filter { it.first in setOf(caretToken, primaryCaretToken, selectionBegin, selectionEnd) }
  val result = SmartList<CaretWithSelection>()

  var tagNo = 0
  fun next(): Pair<String, Int> = caretsTags[tagNo++]

  fun matchSeq(vararg seq: String): Boolean =
    seq.withIndex().all { (index, s) -> caretsTags.getOrNull(index + tagNo)?.first == s }

  while (tagNo < caretsTags.size) {
    when {
      matchSeq(caretToken) -> result.add(CaretWithSelection(next().second))
      matchSeq(primaryCaretToken) -> result.add(CaretWithSelection(next().second, isPrimary = true))
      matchSeq(selectionBegin, selectionEnd) -> {
        val (_, openPos) = next()
        val (_, closePos) = next()
        result.add(CaretWithSelection(closePos, TextRange(openPos, closePos)))
      }
      matchSeq(selectionBegin, caretToken, selectionEnd) -> {
        val (_, openPos) = next()
        val (_, caretPos) = next()
        val (_, closePos) = next()
        result.add(CaretWithSelection(caretPos, TextRange(openPos, closePos)))
      }
      matchSeq(selectionBegin, primaryCaretToken, selectionEnd) -> {
        val (_, openPos) = next()
        val (_, caretPos) = next()
        val (_, closePos) = next()
        result.add(CaretWithSelection(caretPos, TextRange(openPos, closePos), isPrimary = true))
      }
      else -> error("can't match end of carets sequence ${caretsTags.drop(tagNo).joinToString(",")}")
    }
  }

  return result
}

private fun parseFoldings(tags: List<Pair<String, Int>>): List<TextRange> {
  val result = SmartList<TextRange>()
  val foldingsTags = tags.filter { it.first in setOf(foldBegin, foldEnd) }
  var tagNo = 0
  while (tagNo < foldingsTags.size) {
    if (tagNo + 1 < foldingsTags.size && foldingsTags[tagNo].first == foldBegin && foldingsTags[tagNo + 1].first == foldEnd) {
      result.add(TextRange(foldingsTags[tagNo].second, foldingsTags[tagNo + 1].second))
      tagNo += 2
    }
    else {
      error("can't match end of foldings sequence ${foldingsTags.drop(tagNo)}")
    }
  }
  return result
}