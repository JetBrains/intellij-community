// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar
import com.intellij.codeInsight.highlighting.BraceHighlightingHandler
import com.intellij.codeInsight.highlighting.HighlightHandlerBase
import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.ProperTextRange
import com.intellij.openapi.util.Segment
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import kotlin.concurrent.Volatile

/**
 * Repaints/recreates identifier highlighting range highlighters
 * @param myPsiFile may be injected fragment, in which case the `editor` must be corresponding injected editor and  `visibleRange` must have consistent offsets inside the injected document.
 * In both cases, [doCollectInformation] will produce and apply HighlightInfos to the host file.
 */
@ApiStatus.Internal
class IdentifierHighlighterUpdater (
  private val myPsiFile: PsiFile,
  private val myEditor: Editor,
  val context: CodeInsightContext,
  val hostPsiFile: PsiFile
) {
  @RequiresBackgroundThread
  @ApiStatus.Internal
  suspend fun doCollectInformation(hostSession: HighlightingSession, project: Project, hostEditor: Editor, visibleRange: ProperTextRange): IdentifierHighlightingResult {
    val result = IdentifierHighlightingManager.getInstance(project).getMarkupData(myEditor, visibleRange)
    readAction {
      createHighlighters(result, hostEditor, hostSession)
    }
    return result
  }

  private fun createHighlighters(
    result: IdentifierHighlightingResult,
    hostEditor: Editor,
    hostSession: HighlightingSession,
  ): List<HighlightInfo> {
    if (!myEditor.isDisposed()) {
      val infos = createHighlightInfos(result)
      BackgroundUpdateHighlightersUtil.setHighlightersInRange(TextRange(0, hostEditor.document.textLength), infos, hostEditor.getMarkupModel() as MarkupModelEx, getId(), hostSession)
      return infos
    }
    return listOf()
  }

  @ApiStatus.Internal
  fun getCodeBlockMarkerRanges(result: IdentifierHighlightingResult): Collection<Segment> {
    return result.occurrences.mapNotNull { m: IdentifierOccurrence -> if (m.highlightInfoType === HighlightInfoType.ELEMENT_UNDER_CARET_STRUCTURAL) m.range else null }
  }

  private fun getId(): Int {
    var resultId: Int = id
    if (resultId == 0) {
      val registrar =
        TextEditorHighlightingPassRegistrar.getInstance(myPsiFile.getProject()) as TextEditorHighlightingPassRegistrarImpl
      synchronized(IdentifierHighlighterUpdater::class.java) {
        resultId = id
        if (resultId == 0) {
          resultId = registrar.nextAvailableId
          id = resultId
        }
      }
    }
    return resultId
  }

  private fun isCaretOverCollapsedFoldRegion(): Boolean = myEditor.getFoldingModel().getCollapsedRegionAtOffset(myEditor.getCaretModel().offset) != null

  /**
   * Does additional work on code block markers highlighting:
   *  * Draws vertical line covering the scope on the gutter by [com.intellij.codeInsight.highlighting.BraceHighlightingHandler.Companion.lineMarkFragment]
   *  * Schedules preview of the block start if necessary by [com.intellij.codeInsight.highlighting.BraceHighlightingHandler.showScopeHint]
   *
   *
   * In brace matching case this is done from [com.intellij.codeInsight.highlighting.BraceHighlightingHandler.highlightBraces]
   */
  @RequiresEdt
  @ApiStatus.Internal
  fun doAdditionalCodeBlockHighlighting(result: IdentifierHighlightingResult) {
    val myCodeBlockMarkerRanges = getCodeBlockMarkerRanges(result)
    if (myCodeBlockMarkerRanges.size < 2 || myEditor !is EditorEx) {
      return
    }
    val markers: MutableList<Segment> = myCodeBlockMarkerRanges.toMutableList()
    markers.sortWith(Segment.BY_START_OFFSET_THEN_END_OFFSET)
    val leftBraceRange = markers[0]
    val rightBraceRange = markers[markers.size - 1]
    val startLine = myEditor.offsetToLogicalPosition(leftBraceRange.getStartOffset()).line
    val endLine = myEditor.offsetToLogicalPosition(rightBraceRange.getEndOffset()).line
    if (endLine - startLine > 0) {
      BraceHighlightingHandler.lineMarkFragment(myEditor, myEditor.getDocument(), startLine, endLine, true)
    }

    BraceHighlightingHandler.showScopeHint(myEditor, myPsiFile, leftBraceRange.getStartOffset(), leftBraceRange.getEndOffset())
  }

  internal fun createHighlightInfos(markupInfos: IdentifierHighlightingResult): List<HighlightInfo> {
    if (EditorUtil.isCaretInVirtualSpace(myEditor) || isCaretOverCollapsedFoldRegion()) {
      return listOf()
    }
    if (markupInfos.occurrences.isEmpty()) {
      return listOf()
    }
    val existingMarkupTooltips: MutableSet<Pair<String, Segment>> = HashSet()
    for (highlighter in myEditor.getMarkupModel().getAllHighlighters()) {
      val tooltip = highlighter.getErrorStripeTooltip()
      if (tooltip is String) {
        existingMarkupTooltips.add(Pair.create(tooltip, highlighter.textRange))
      }
    }

    val result = markupInfos.occurrences.map { m: IdentifierOccurrence ->
      createHighlightInfo(m.range, m.highlightInfoType, existingMarkupTooltips)
    }
    return result
  }

  private fun createHighlightInfo(
    range: Segment,
    type: HighlightInfoType,
    existingMarkupTooltips: Set<Pair<String, Segment>>
  ): HighlightInfo {
    val start = range.getStartOffset()
    val tooltip = if (start <= myEditor.getDocument().textLength) HighlightHandlerBase.getLineTextErrorStripeTooltip(
      myEditor.getDocument(), start, false)
    else null
    val unescapedTooltip = if (existingMarkupTooltips.contains(Pair(tooltip, range))) null else tooltip
    val builder = HighlightInfo.newHighlightInfo(type).range(TextRange.create(range)).group(getId())
    if (unescapedTooltip != null) {
      builder.unescapedToolTip(unescapedTooltip)
    }
    val info = builder.createUnconditionally()
    info.setToolId(IdentifierHighlighterUpdater::class.java)
    return info
  }

  @RequiresBackgroundThread
  @ApiStatus.Internal
  @TestOnly
  fun doCollectInformationForTestsSynchronously(hostSession: HighlightingSession): List<HighlightInfo> {
    val result =
      IdentifierHighlightingComputer(myPsiFile, myEditor, ProperTextRange.create(myPsiFile.textRange), myEditor.caretModel.offset).computeRanges()
    return createHighlighters(result, myEditor, hostSession)
  }

  companion object {
    @Volatile
    private var id = 0

    fun clearMyHighlights(document: Document, project: Project) {
      val markupModel = DocumentMarkupModel.forDocument(document, project, true)
      for (highlighter in markupModel.getAllHighlighters()) {
        val info = HighlightInfo.fromRangeHighlighter(highlighter)
        if (info != null &&
            (info.type === HighlightInfoType.ELEMENT_UNDER_CARET_READ || info.type === HighlightInfoType.ELEMENT_UNDER_CARET_WRITE)
        ) {
          highlighter.dispose()
        }
      }
    }
  }
}