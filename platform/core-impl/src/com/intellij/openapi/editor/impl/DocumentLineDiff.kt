// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.ex.DocumentText
import com.intellij.openapi.editor.ex.DocumentTextPatch
import com.intellij.util.diff.Diff
import com.intellij.util.diff.FilesTooBigForDiffException
import com.intellij.util.text.MergingCharSequence
import org.jetbrains.annotations.TestOnly

/**
 * Lazily computes and caches a line diff for one text replacement.
 *
 * The replacement fragments and offset use the narrowed document-event range when this instance is shared with an
 * event. Standalone snapshot patches derive the same data from their applied range.
 */
internal class DocumentLineDiff(
  val changeStartOffset: Int,
  private val oldFragment: CharSequence,
  private val newFragment: CharSequence,
) {
  private var oldFragmentLineSet: LineSet? = null
  private var oldFragmentLineSetStart: Int = changeStartOffset
  private var changesInitialized: Boolean = false
  private var changesUnavailable: Boolean = false
  private var changes: Diff.Change? = null

  @TestOnly
  fun isComputed(): Boolean = changesInitialized

  @Throws(FilesTooBigForDiffException::class)
  fun translateLine(line: Int, changeStartLine: Int, afterText: CharSequence): Int {
    var change = changes(afterText) ?: return line
    val relativeLine = line - changeStartLine
    var translatedLine = relativeLine

    while (true) {
      if (relativeLine < change.line0) break
      if (relativeLine >= change.line0 + change.deleted) {
        translatedLine += change.inserted - change.deleted
      }
      else {
        val delta = minOf(change.inserted, relativeLine - change.line0)
        translatedLine = change.line1 + delta
        break
      }
      change = change.link ?: break
    }

    return translatedLine + changeStartLine
  }

  @Throws(FilesTooBigForDiffException::class)
  fun translateLineStrict(line: Int, changeStartLine: Int, afterText: CharSequence): Int {
    val changes = changes(afterText) ?: return line
    if (line < changeStartLine) return line
    val translatedLine = Diff.translateLine(changes, line - changeStartLine)
    return if (translatedLine < 0) -1 else translatedLine + changeStartLine
  }

  fun getOldFragmentLineSet(afterText: CharSequence): LineSet {
    initializeOldFragmentLineSet(afterText)
    return checkNotNull(oldFragmentLineSet)
  }

  fun getOldFragmentLineSetStart(afterText: CharSequence): Int {
    initializeOldFragmentLineSet(afterText)
    return oldFragmentLineSetStart
  }

  @Throws(FilesTooBigForDiffException::class)
  private fun changes(afterText: CharSequence): Diff.Change? {
    if (changesUnavailable) throw FilesTooBigForDiffException()
    if (!changesInitialized) {
      try {
        changes = Diff.buildChanges(oldLines(afterText), Diff.splitLines(newFragment))
        changesInitialized = true
      }
      catch (exception: FilesTooBigForDiffException) {
        changesUnavailable = true
        throw exception
      }
    }
    return changes
  }

  private fun oldLines(afterText: CharSequence): Array<String> {
    val lineSet = getOldFragmentLineSet(afterText)
    val offsetDiff = changeStartOffset - oldFragmentLineSetStart
    val lines = ArrayList<String>(lineSet.lineCount)
    val lineIterator = lineSet.createIterator()
    while (!lineIterator.atEnd()) {
      val start = lineIterator.start - offsetDiff
      val end = lineIterator.end - lineIterator.separatorLength - offsetDiff
      if (start >= 0 && end <= oldFragment.length) {
        lines.add(oldFragment.subSequence(start, end).toString())
      }
      lineIterator.advance()
    }
    return if (lines.isEmpty()) arrayOf("") else lines.toTypedArray()
  }

  private fun initializeOldFragmentLineSet(afterText: CharSequence) {
    if (oldFragmentLineSet != null) return

    var fragment = oldFragment
    if (oldFragmentLineSetStart > 0 && afterText[oldFragmentLineSetStart - 1] == '\r') {
      oldFragmentLineSetStart--
      fragment = MergingCharSequence("\r", fragment)
    }
    val newChangeEnd = changeStartOffset + newFragment.length
    if (newChangeEnd < afterText.length && afterText[newChangeEnd] == '\n') {
      fragment = MergingCharSequence(fragment, "\n")
    }
    oldFragmentLineSet = LineSet.createLineSet(fragment)
  }
}

internal fun DocumentTextPatch.attachLineDiff(lineDiff: DocumentLineDiff) {
  val patch = this as? SimpleTextPatch ?: return
  check(patch.lineDiff == null || patch.lineDiff === lineDiff) {
    "DocumentTextPatch is already associated with another line diff"
  }
  patch.lineDiff = lineDiff
}

internal fun DocumentTextPatch.lineDiff(beforeText: DocumentText): DocumentLineDiff {
  val patch = this as? SimpleTextPatch
  patch?.lineDiff?.let { return it }

  val lineDiff = DocumentLineDiff(
    changeStartOffset = startOffset(),
    oldFragment = beforeText.chars().subSequence(startOffset(), endOffset()),
    newFragment = newFragment(),
  )
  if (patch != null) {
    patch.lineDiff = lineDiff
  }
  return lineDiff
}
