// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.merge

import com.intellij.codeInsight.editorActions.CopyPastePostProcessor
import com.intellij.codeInsight.editorActions.ReferenceCopyPasteProcessor
import com.intellij.codeInsight.editorActions.TextBlockTransferableData
import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.diff.tools.util.text.MergeInnerDifferences
import com.intellij.diff.util.DiffUtil
import com.intellij.diff.util.Side
import com.intellij.diff.util.ThreeSide
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.progress.DumbProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import java.util.concurrent.ConcurrentHashMap

internal class ChangeReferenceProcessor(private val project: Project, private val editor: Editor, private val files: List<PsiFile>, private val documents: List<Document>) {

  private val innerDifferencesCache: MutableMap<TextMergeChange, MergeInnerDifferences?> = ConcurrentHashMap()

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
    val sourceThreeSide = sourceSide.select(ThreeSide.LEFT, ThreeSide.RIGHT)
    val sourceDocument = sourceThreeSide.select(documents)
    val psiFile = sourceSide.select(files[0], files[2])
    val sourceRange = DiffUtil.getLinesRange(sourceDocument, change.getStartLine(sourceThreeSide), change.getEndLine(sourceThreeSide))

    if (change.getStartLine(sourceThreeSide) == change.getEndLine(sourceThreeSide)) return

    if (processInnerFragments) {
      val innerDifferences = compareInner(change) ?: return

      innerDifferences.get(sourceThreeSide)?.forEach { textRange ->
        if (textRange.isEmpty) return@forEach

        val baseDocument = editor.document
        val text = baseDocument.getText(rangeMarker.textRange)
        val fragmentStartOffset = sourceRange.startOffset + textRange.startOffset
        val rangeInDocument = TextRange(fragmentStartOffset, fragmentStartOffset + textRange.length)
        val fragmentText = sourceDocument.getText(rangeInDocument)

        if (fragmentText.isBlank()) return@forEach

        val offset = text.indexOf(fragmentText)
        if (offset == -1) return@forEach

        val data = createReferenceData(psiFile, rangeInDocument.startOffset, rangeInDocument.endOffset)
        val marker = baseDocument.createRangeMarker(
          rangeMarker.startOffset + offset,
          rangeMarker.startOffset + offset + fragmentText.length
        )
        data.forEach { processorData ->
          processorData.process(project, editor, marker, 0, Ref(false))
        }
      }
    }
    else {
      val data = createReferenceData(psiFile, sourceRange.startOffset, sourceRange.endOffset)
      data.forEach { processorData ->
        processorData.process(project, editor, rangeMarker, 0, Ref(false))
      }
    }
  }

  private fun getSequences(change: TextMergeChange): List<CharSequence?> {
    return ThreeSide.map {
      if (!change.isChange(it)) return@map null
      val startLine = change.getStartLine(it)
      val endLine = change.getEndLine(it)
      if (startLine == endLine) return@map null
      return@map DiffUtil.getLinesContent(it.select(documents), startLine, endLine)
    }
  }

  private fun compareInner(change: TextMergeChange): MergeInnerDifferences? {
    return innerDifferencesCache.computeIfAbsent(change) {
      return@computeIfAbsent DiffUtil.compareThreesideInner(getSequences(change), ComparisonPolicy.DEFAULT, DumbProgressIndicator.INSTANCE)
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
