// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.highlighting.HighlightHandlerBase
import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.Segment
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class IdentifierHighlighterUpdater(
  private val myPsiFile: PsiFile,
  private val myEditor: Editor,
  val context: CodeInsightContext,
  val hostPsiFile: PsiFile,
) {
  fun createHighlightInfos(markupInfos: IdentifierHighlightingResult, id:Int): List<HighlightInfo> {
    if (EditorUtil.isCaretInVirtualSpace(myEditor) || isCaretOverCollapsedFoldRegion()) {
      return listOf()
    }
    if (markupInfos.occurrences.isEmpty()) {
      return listOf()
    }
    val existingMarkupTooltips: MutableSet<com.intellij.openapi.util.Pair<String, Segment>> = HashSet()
    for (highlighter in myEditor.getMarkupModel().getAllHighlighters()) {
      val tooltip = highlighter.getErrorStripeTooltip()
      if (tooltip is String) {
        existingMarkupTooltips.add(com.intellij.openapi.util.Pair.create(tooltip, highlighter.textRange))
      }
    }

    val result = markupInfos.occurrences.map { m: IdentifierOccurrence ->
      createHighlightInfo(m.range, m.highlightInfoType, existingMarkupTooltips, id)
    }
    return result
  }

  private fun createHighlightInfo(
    range: Segment,
    type: HighlightInfoType,
    existingMarkupTooltips: Set<Pair<String, Segment>>,
    id: Int,
  ): HighlightInfo {
    val start = range.getStartOffset()
    val tooltip = if (start <= myEditor.getDocument().textLength) HighlightHandlerBase.getLineTextErrorStripeTooltip(
      myEditor.getDocument(), start, false)
    else null
    val unescapedTooltip = if (existingMarkupTooltips.contains(Pair(tooltip, range))) null else tooltip
    val builder = HighlightInfo.newHighlightInfo(type).range(TextRange.create(range)).group(id)
    if (unescapedTooltip != null) {
      builder.unescapedToolTip(unescapedTooltip)
    }
    val info = builder.createUnconditionally()
    info.setToolId(IdentifierHighlighterUpdater::class.java)
    return info
  }
  private fun isCaretOverCollapsedFoldRegion(): Boolean {
    return myEditor.getFoldingModel().getCollapsedRegionAtOffset(myEditor.getCaretModel().getOffset()) != null
  }
}