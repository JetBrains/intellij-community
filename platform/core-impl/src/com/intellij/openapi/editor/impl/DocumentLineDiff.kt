// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.ex.DocumentText
import com.intellij.openapi.editor.ex.DocumentTextPatch
import com.intellij.util.diff.Diff
import com.intellij.util.diff.FilesTooBigForDiffException
import com.intellij.util.text.ImmutableCharSequence
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
  oldFragment: CharSequence,
  newFragment: CharSequence,
) {
  private val oldFragment: CharSequence = ImmutableCharSequence.asImmutable(oldFragment)
  private val newFragment: CharSequence = ImmutableCharSequence.asImmutable(newFragment)

  /**
   * this line diff can be shared by an event and patches applied to different snapshots, while old-fragment line splitting depends
   * on whether the changed fragment is adjacent to `\r` and `\n` in each after-text.
   *
   * So we have a lazily allocated four-slot cache indexed by [boundaryContext]. Each entry contains the old-fragment line set and start offset
   * for one boundary combination, and owns the corresponding lazily computed [Diff.Change].
   *
   * How it updates: a missing entry is added under this instance's monitor by publishing a copied array through the volatile field.
   * Published line-set data is immutable; only that entry's diff result transitions once, under the entry's monitor.
   */
  @Volatile
  private var oldFragmentDataByContext: Array<OldFragmentData?>? = null

  @TestOnly
  fun isComputed(): Boolean = oldFragmentDataByContext?.any { it?.changesResult is ChangesResult.Available } == true

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
    return oldFragmentData(afterText).lineSet
  }

  fun getOldFragmentLineSetStart(afterText: CharSequence): Int {
    return oldFragmentData(afterText).startOffset
  }

  @Throws(FilesTooBigForDiffException::class)
  private fun changes(afterText: CharSequence): Diff.Change? {
    val fragmentData = oldFragmentData(afterText)
    return when (val result = fragmentData.changesResult ?: initializeChanges(fragmentData)) {
      is ChangesResult.Available -> result.changes
      ChangesResult.FileTooBig -> throw FilesTooBigForDiffException()
    }
  }

  private fun initializeChanges(fragmentData: OldFragmentData): ChangesResult {
    return synchronized(fragmentData) {
      fragmentData.changesResult ?: try {
        ChangesResult.Available(Diff.buildChanges(oldLines(fragmentData), Diff.splitLines(newFragment)))
      }
      catch (_: FilesTooBigForDiffException) {
        ChangesResult.FileTooBig
      }.also { fragmentData.changesResult = it }
    }
  }

  private fun oldLines(fragmentData: OldFragmentData): Array<String> {
    val offsetDiff = changeStartOffset - fragmentData.startOffset
    val lines = ArrayList<String>(fragmentData.lineSet.lineCount)
    val lineIterator = fragmentData.lineSet.createIterator()
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

  private fun oldFragmentData(afterText: CharSequence): OldFragmentData {
    val context = boundaryContext(afterText)
    oldFragmentDataByContext?.get(context)?.let { return it }
    return synchronized(this) {
      val dataByContext = oldFragmentDataByContext
      dataByContext?.get(context) ?: createOldFragmentData(context).also { fragmentData ->
        oldFragmentDataByContext = (dataByContext?.copyOf() ?: arrayOfNulls(CONTEXT_COUNT)).also {
          it[context] = fragmentData
        }
      }
    }
  }

  private fun boundaryContext(afterText: CharSequence): Int {
    var context = 0
    if (changeStartOffset > 0 && afterText[changeStartOffset - 1] == '\r') {
      context = context or PREPEND_CARRIAGE_RETURN
    }
    val newChangeEnd = changeStartOffset + newFragment.length
    if (newChangeEnd < afterText.length && afterText[newChangeEnd] == '\n') {
      context = context or APPEND_LINE_FEED
    }
    return context
  }

  private fun createOldFragmentData(context: Int): OldFragmentData {
    var fragment = oldFragment
    var lineSetStart = changeStartOffset
    if (context and PREPEND_CARRIAGE_RETURN != 0) {
      lineSetStart--
      fragment = MergingCharSequence("\r", fragment)
    }
    if (context and APPEND_LINE_FEED != 0) {
      fragment = MergingCharSequence(fragment, "\n")
    }
    return OldFragmentData(LineSet.createLineSet(fragment), lineSetStart)
  }

  private class OldFragmentData(val lineSet: LineSet, val startOffset: Int) {
    @Volatile
    var changesResult: ChangesResult? = null
  }

  private sealed interface ChangesResult {
    data class Available(val changes: Diff.Change?) : ChangesResult
    data object FileTooBig : ChangesResult
  }

  private companion object {
    private const val PREPEND_CARRIAGE_RETURN = 1
    private const val APPEND_LINE_FEED = 2
    private const val CONTEXT_COUNT = 4
  }
}

internal fun DocumentTextPatch.attachLineDiff(lineDiff: DocumentLineDiff) {
  if (this is SimpleTextPatch) {
    attachLineDiffCache(lineDiff)
  }
}

internal fun DocumentTextPatch.lineDiff(beforeText: DocumentText): DocumentLineDiff {
  val patch = this as? SimpleTextPatch
  if (patch != null) return patch.getOrCreateLineDiff(beforeText)

  return DocumentLineDiff(
    changeStartOffset = startOffset(),
    oldFragment = beforeText.chars().subtext(startOffset(), endOffset()),
    newFragment = newFragment(),
  )
}
