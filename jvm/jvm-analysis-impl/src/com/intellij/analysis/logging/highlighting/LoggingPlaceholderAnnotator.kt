// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.logging.highlighting

import com.intellij.analysis.customization.console.ClassFinderConsoleColorsPage
import com.intellij.analysis.logging.resolve.getAlignedPlaceholderCount
import com.intellij.analysis.logging.resolve.getContext
import com.intellij.analysis.logging.resolve.getPlaceholderRanges
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.expressions.UInjectionHost
import org.jetbrains.uast.toUElementOfType

class LoggingPlaceholderAnnotator : Annotator {
  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    val literalExpression = element.toUElementOfType<UInjectionHost>() ?: return
    val textRangeList = getRanges(literalExpression) ?: return
    val startOffset = element.textRange.startOffset
    val endOffset = element.textRange.endOffset

    textRangeList.forEach { range ->
      val shiftedRange = range.shiftRight(startOffset)
      assert(startOffset <= shiftedRange.startOffset && shiftedRange.endOffset <= endOffset)
      holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(shiftedRange).textAttributes(ClassFinderConsoleColorsPage.LOG_STRING_PLACEHOLDER).create()
    }
  }

  private fun getRanges(uExpression: UExpression): List<TextRange>? {
    val context = getContext(uExpression) ?: return null

    var placeholderRangesList = getPlaceholderRanges(context) ?: return null

    val placeholderParametersSize = context.placeholderParameters.size
    if (placeholderParametersSize < placeholderRangesList.size) {
      placeholderRangesList = placeholderRangesList.dropLast(placeholderRangesList.size - placeholderParametersSize)
    }
    val textRangeList = placeholderRangesList.flatMap {
      val ranges = it.ranges
      if (ranges.any { range -> range == null }) return null
      ranges.filterNotNull()
    }

    return getAlignedPlaceholderCount(textRangeList, context)
  }
}