// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.merge

import com.intellij.codeInsight.editorActions.CopyPastePostProcessor
import com.intellij.codeInsight.editorActions.TextBlockTransferableData
import com.intellij.diff.comparison.ComparisonManager
import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.diff.comparison.DiffTooBigException
import com.intellij.diff.fragments.MergeLineFragment
import com.intellij.diff.tools.util.DiffDataKeys
import com.intellij.diff.tools.util.text.LineOffsetsUtil
import com.intellij.diff.util.LineRange
import com.intellij.diff.util.MergeRange
import com.intellij.diff.util.ThreeSide
import com.intellij.lang.imports.ImportBlockRangeProvider
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.editor.Document
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
    fun getImportMergeRange(project: Project?, psiFiles: MutableList<PsiFile>): MergeRange? {
      if (project == null || psiFiles.size != 3) return null

      val ranges = ArrayList<LineRange>()
      for (side in ThreeSide.entries) {
        val psiFile = side.select(psiFiles) ?: return null
        val importRange = getImportLineRange(psiFile) ?: return null
        ranges.add(importRange)
      }
      return MergeRange(ranges[0].start, ranges[0].end,
                        ranges[1].start, ranges[1].end,
                        ranges[2].start, ranges[2].end)
    }

    @JvmStatic
    fun getPsiFile(side: ThreeSide, project: Project, mergeRequest: TextMergeRequest): PsiFile? {
      val sourceDocument = side.select(mergeRequest.contents).document
      val file = FileDocumentManager.getInstance().getFile(sourceDocument)
      if (file == null) return null
      return PsiManager.getInstance(project).findFile(file)
    }

    private fun getImportLineRange(psiFile: PsiFile): LineRange? {
      val range = ImportBlockRangeProvider.getRange(psiFile) ?: return null
      val document = psiFile.fileDocument
      val startLine = document.getLineNumber(range.startOffset)
      val endLine = if(range.startOffset == range.endOffset) startLine else document.getLineNumber(range.endOffset) + 1
      return LineRange(startLine, endLine)
    }

    fun isEnabledFor(project: Project?, document: Document): Boolean {
      if (project == null) return false
      val file = FileDocumentManager.getInstance().getFile(document) ?: return false
      val psiFile = PsiManager.getInstance(project).findFile(file) ?: return false
      return ImportBlockRangeProvider.isFileSupported(psiFile)
    }
  }
}

/**
 * @property fragments The list of merged line fragments.
 * @property importBlockStart The start index of import range (inclusive).
 * @property importBlockEnd The end index of import range (exclusive).
 */
@Internal
data class MergeLineFragmentsWithImportMetadata(val fragments: List<MergeLineFragment>, val importBlockStart: Int, val importBlockEnd: Int) {
  constructor(fragments: List<MergeLineFragment>) : this(fragments, -1, -1)

  fun isIndexInImportRange(index: Int) = index in importBlockStart..<importBlockEnd
}

@Internal
data class ProcessorData<T : TextBlockTransferableData>(val processor: CopyPastePostProcessor<T>, val data: List<T>) {
  fun process(project: Project, editor: Editor, bounds: RangeMarker, caretOffset: Int, indented: Ref<in Boolean>) {
    if (data.isEmpty()) return
    processor.processTransferableData(project, editor, bounds, caretOffset, indented, data)
  }
}

class MergeReferenceData(private val left: List<ProcessorData<*>>,
                         private val right: List<ProcessorData<*>>) {
  fun getReferenceData(side: ThreeSide): List<ProcessorData<*>> = side.selectNotNull(left, emptyList(), right)
}

internal class ResolveConflictsInImportsToggleAction : ToggleAction() {

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    super.update(e)

    val viewer = getMergeViewer(e)
    if (viewer == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    e.presentation.isEnabled = viewer.myResolveImportsPossible && MergeImportUtil.isEnabledFor(viewer.project, viewer.editor.document)
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    return getMergeViewer(e)?.textSettings?.isAutoResolveImportConflicts ?: false
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    getMergeViewer(e)?.textSettings?.isAutoResolveImportConflicts = state
  }

  private fun getMergeViewer(e: AnActionEvent): MergeThreesideViewer? {
    val textMergeViewer = e.getData(DiffDataKeys.MERGE_VIEWER) as? TextMergeViewer
    return textMergeViewer?.viewer
  }
}