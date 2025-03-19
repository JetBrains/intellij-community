// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.microservices.references

import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ContributedReferencesAnnotator
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.microservices.url.references.UrlSegmentReference
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.templateLanguages.OuterLanguageElement
import com.intellij.psi.util.childrenOfType
import com.intellij.psi.util.startOffset
import com.intellij.util.text.TextRangeUtil
import com.intellij.util.text.splitToTextRanges

internal open class MicroserviceReferenceAnnotator : ContributedReferencesAnnotator {

  override fun annotate(element: PsiElement, references: List<PsiReference>, holder: AnnotationHolder) {
    if (holder.isBatchMode || !(element is PsiLanguageInjectionHost || element is ContributedReferenceHost)) {
      return
    }

    if (references.find { it is UrlSegmentReference } != null) {
      val rangeInElement = ElementManipulators.getValueTextRange(element)
      val lineRanges = splitToTextRanges(rangeInElement.substring(element.text), "\n", false)
        .map { subRange ->
          val text = subRange.substring(element.text)
          val leftStrip = (0..text.lastIndex).firstOrNull { !text[it].isWhitespace() } ?: text.length
          val rightStrip = text.lastIndex - ((text.lastIndex downTo 0).firstOrNull { !text[it].isWhitespace() } ?: -1)
          val onlySpaces = leftStrip == text.length

          if (!onlySpaces) {
            TextRange.create(subRange.startOffset + leftStrip, subRange.endOffset - rightStrip)
          }
          else {
            subRange
          }
        }
        .map { it.shiftRight(rangeInElement.startOffset) }
      for (range in lineRanges) {
        highlightRange(range, element, holder)
      }
    }
  }

  private fun highlightRange(rangeInElement: TextRange, element: PsiElement, holder: AnnotationHolder) {
    if (rangeInElement.isEmpty) return

    val injectedRanges = ArrayList<TextRange>().also { injectedRanges ->
      InjectedLanguageManager.getInstance(element.project).enumerate(element) { _, places ->
        places.mapTo(injectedRanges) { it.rangeInsideHost.shiftRight(element.startOffset) }
      }
    }
    val templateRanges = element.childrenOfType<OuterLanguageElement>().map { it.textRange }
    val elementOwnRanges = TextRangeUtil.excludeRanges(rangeInElement.shiftRight(element.textRange.startOffset), templateRanges + injectedRanges)
    for (range in elementOwnRanges) {
      holder.newSilentAnnotation(HighlightInfoType.HIGHLIGHTED_REFERENCE_SEVERITY)
        .range(range)
        .textAttributes(CodeInsightColors.INACTIVE_HYPERLINK_ATTRIBUTES)
        .create()
    }
  }
}