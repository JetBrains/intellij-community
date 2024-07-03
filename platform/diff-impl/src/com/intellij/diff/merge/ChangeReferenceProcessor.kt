// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.merge

import com.intellij.codeInsight.editorActions.CopyPastePostProcessor
import com.intellij.codeInsight.editorActions.ReferenceCopyPasteProcessor
import com.intellij.codeInsight.editorActions.TextBlockTransferableData
import com.intellij.diff.util.Side
import com.intellij.diff.util.ThreeSide
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile

class ChangeReferenceProcessor(val project: Project, private val editor: Editor, val files: List<PsiFile>) {

  fun process(
    side: ThreeSide,
    changes: List<TextMergeChange>,
    newRanges: List<RangeMarker>,
  ) {
    try {
      for (i in changes.indices) {
        val change = changes[i]
        val marker = newRanges[i]
        if (side == ThreeSide.BASE && change.isConflict) {
          transferReferences(Side.LEFT, change, marker, true)
          transferReferences(Side.RIGHT, change, marker, true)
        }
        else {
          val sourceSide = side.select(Side.LEFT, if (change.isChange(Side.LEFT)) Side.LEFT else Side.RIGHT, Side.RIGHT) ?: return
          transferReferences(sourceSide, change, marker, false)
        }
      }
    }
    catch (e: ProcessCanceledException) {
      throw e
    }
    catch (e: Exception) {
      LOG.error(e)
    }
  }

  private fun transferReferences(
    sourceSide: Side,
    change: TextMergeChange,
    rangeMarker: RangeMarker,
    processInnerFragments: Boolean,
  ) {
    val sourceThreeSide = sourceSide.select(ThreeSide.LEFT, ThreeSide.RIGHT) ?: return
    val psiFile = sourceSide.select(files[0], files[2]) ?: return
    val sourceDocument = psiFile.fileDocument
    val startOffset = sourceDocument.getLineStartOffset(change.getStartLine(sourceThreeSide))
    val endOffset = sourceDocument.getLineEndOffset(change.getEndLine(sourceThreeSide) - 1)

    if (!processInnerFragments) {
      val data = createReferenceData(psiFile, startOffset, endOffset)
      data.forEach { processorData ->
        processorData.process(project, editor, rangeMarker, 0, Ref(false))
      }
      return
    }

    val innerFragments = change.innerFragments
    innerFragments?.get(sourceThreeSide)?.forEach {
      if (it.isEmpty) return@forEach

      val text = editor.document.getText(rangeMarker.textRange)
      val fragmentStartOffset = startOffset + it.startOffset
      val rangeInDocument = TextRange(fragmentStartOffset, fragmentStartOffset + it.length)
      val fragmentText = sourceDocument.getText(rangeInDocument)
      val offset = text.indexOf(fragmentText)

      if (offset == -1) return@forEach

      val data = createReferenceData(psiFile, rangeInDocument.startOffset, rangeInDocument.endOffset)
      val marker = sourceDocument.createRangeMarker(
        rangeMarker.startOffset + offset,
        rangeInDocument.startOffset + offset + it.length
      )
      data.forEach { processorData ->
        processorData.process(project, editor, marker, 0, Ref(false))
      }
    }
  }

  private fun <T : TextBlockTransferableData> createProcessorData(
    processor: CopyPastePostProcessor<T>,
    editor: Editor,
    psiFile: PsiFile,
    startOffset: Int,
    endOffset: Int,
  ): ProcessorData<T> {
    val processorData = processor.collectTransferableData(psiFile, editor, intArrayOf(startOffset), intArrayOf(endOffset))
    return ProcessorData(processor, processorData)
  }

  private fun createReferenceData(psiFile: PsiFile, startOffset: Int, endOffset: Int): List<ProcessorData<*>> {
    return CopyPastePostProcessor.EP_NAME.extensionList.filterIsInstance<ReferenceCopyPasteProcessor>().map {
      createProcessorData(it as CopyPastePostProcessor<*>, editor, psiFile, startOffset, endOffset)
    }
  }

  companion object {
    val LOG = Logger.getInstance(ChangeReferenceProcessor::class.java)
  }
}
