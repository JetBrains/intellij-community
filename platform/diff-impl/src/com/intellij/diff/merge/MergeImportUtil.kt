// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.merge

import com.intellij.codeInsight.editorActions.CopyPastePostProcessor
import com.intellij.codeInsight.editorActions.TextBlockTransferableData
import com.intellij.diff.comparison.ComparisonManager
import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.diff.comparison.DiffTooBigException
import com.intellij.diff.fragments.MergeLineFragment
import com.intellij.diff.tools.util.text.LineOffsetsUtil
import com.intellij.diff.util.LineRange
import com.intellij.diff.util.MergeRange
import com.intellij.diff.util.ThreeSide
import com.intellij.lang.imports.ImportBlockRangeProvider.Companion.getRange
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.*

@Internal
class MergeImportUtil {
  companion object {

    @JvmStatic
    @Throws(DiffTooBigException::class)
    fun getDividedFromImportsFragments(sequences: List<CharSequence>,
                                       policy: ComparisonPolicy,
                                       importRange: MergeRange,
                                       indicator: ProgressIndicator): MergeLineFragmentsWithImportMetadata {
      val manager = ComparisonManager.getInstance()
      val result = mutableListOf<MergeLineFragment>()
      result += manager.mergeLinesWithinRange(sequences[0], sequences[1], sequences[2], policy,
                                              MergeRange(0, importRange.start1,
                                                         0, importRange.start2,
                                                         0, importRange.start3),
                                              indicator)

      val importBlockStart = result.size
      result += manager.mergeLinesWithinRange(sequences[0], sequences[1], sequences[2], policy,
                                              MergeRange(importRange.start1, importRange.end1,
                                                         importRange.start2, importRange.end2,
                                                         importRange.start3, importRange.end3),
                                              indicator)
      val importBlockEnd = result.size
      result += manager.mergeLinesWithinRange(sequences[0], sequences[1], sequences[2], policy,
                                              MergeRange(importRange.end1, LineOffsetsUtil.create(sequences[0]).lineCount,
                                                         importRange.end2, LineOffsetsUtil.create(sequences[1]).lineCount,
                                                         importRange.end3, LineOffsetsUtil.create(sequences[2]).lineCount),
                                              indicator)

      return MergeLineFragmentsWithImportMetadata(result, importBlockStart, importBlockEnd)
    }

    @JvmStatic
    fun getImportMergeRange(project: Project?, mergeRequest: TextMergeRequest): MergeRange? {
      if (project == null) return null
      val files = listOf(getPsiFile(ThreeSide.LEFT, project, mergeRequest), getPsiFile(ThreeSide.BASE, project, mergeRequest), getPsiFile(ThreeSide.RIGHT, project, mergeRequest))
      val ranges = files.mapNotNull(this::getImportLineRange)
      if (ranges.size != 3) return null
      return MergeRange(ranges[0].start, ranges[0].end + 1, ranges[1].start, ranges[1].end + 1, ranges[2].start, ranges[2].end + 1)
    }

    private fun getPsiFile(side: ThreeSide, project: Project, mergeRequest: TextMergeRequest): PsiFile? {
      val sourceDocument = side.select(mergeRequest.contents).document
      val file = FileDocumentManager.getInstance().getFile(sourceDocument)
      if (file == null) return null
      return PsiManager.getInstance(project).findFile(file)
    }

    private fun getImportLineRange(file: PsiFile?): LineRange? {
      if (file == null) return null
      val range = getRange(file)
      if (range == null) return null
      val document = file.fileDocument
      return LineRange(document.getLineNumber(range.startOffset), document.getLineNumber(range.endOffset))
    }

  }
}

/**
 * @property fragments The list of merged line fragments.
 * @property importBlockStart The start index of import range (inclusive).
 * @property importBlockEnd The end index of import range (exclusive).
 */
@Internal
data class MergeLineFragmentsWithImportMetadata(val fragments: List<MergeLineFragment>, val importBlockStart: Int = -1, val importBlockEnd: Int = -1) {
  fun isIndexInImportRange(index: Int) = index in importBlockStart..<importBlockEnd
}

@Internal
data class ProcessorData<T : TextBlockTransferableData>(val processor: CopyPastePostProcessor<T>, val data: List<T>) {
  fun process(project: Project, editor: Editor, bounds: RangeMarker, caretOffset: Int, indented: Ref<in Boolean>) {
    if (data.isEmpty()) return
    processor.processTransferableData(project, editor, bounds, caretOffset, indented, data)
  }
}

class MergeReferenceData(private val sideToData: Map<ThreeSide, List<ProcessorData<TextBlockTransferableData>>>) {
  fun getReferenceData(side: ThreeSide): List<ProcessorData<TextBlockTransferableData>> = sideToData[side] ?: emptyList()
}