// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.navigation

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.lang.documentation.psi.isNavigatableQuickDoc
import com.intellij.lang.documentation.psi.psiDocumentationTargets
import com.intellij.lang.documentation.symbol.impl.symbolDocumentationTargets
import com.intellij.model.Symbol
import com.intellij.model.psi.PsiSymbolService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.HintText
import com.intellij.openapi.util.TextRange
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * @param ranges absolute ranges to highlight
 * @param isNavigatable whether to apply link highlighting
 * @param hintText HTML of the hint
 * @param target target, which generated the [hintText], or `null` if [hintText] is a simple message; used to resolve links in [hintText]
 */
@Internal
class CtrlMouseData(
  val ranges: List<TextRange>,
  val isNavigatable: Boolean,
  val hintText: @HintText String?,
  val target: DocumentationTarget?,
)

internal fun rangeOnlyCtrlMouseData(ranges: List<TextRange>): CtrlMouseData = CtrlMouseData(
  ranges,
  isNavigatable = true,
  hintText = null,
  target = null,
)

internal fun multipleTargetsCtrlMouseData(ranges: List<TextRange>): CtrlMouseData = CtrlMouseData(
  ranges,
  isNavigatable = true,
  hintText = CodeInsightBundle.message("multiple.implementations.tooltip"),
  target = null,
)

internal fun symbolCtrlMouseData(
  project: Project,
  symbol: Symbol,
  elementAtOffset: PsiElement,
  ranges: List<TextRange>,
  declared: Boolean,
): CtrlMouseData {
  val psi = PsiSymbolService.getInstance().extractElementFromSymbol(symbol)
  if (psi != null) {
    return targetCtrlMouseData(
      ranges,
      isNavigatable = declared || isNavigatableQuickDoc(elementAtOffset, psi),
      target = psiDocumentationTargets(psi, elementAtOffset).first() //TODO support multi-targeting
    )
  }
  return targetCtrlMouseData(
    ranges,
    true, // non-PSI are always navigatable
    target = symbolDocumentationTargets(project, symbol).firstOrNull(),
  )
}

internal fun psiCtrlMouseData(
  leafElement: PsiElement,
  targetElement: PsiElement,
): CtrlMouseData {
  return targetCtrlMouseData(
    ranges = getReferenceRanges(leafElement),
    isNavigatable = isNavigatableQuickDoc(leafElement, targetElement),
    target = psiDocumentationTargets(targetElement, leafElement).first() //TODO support multi-targeting
  )
}

@Internal
internal fun getReferenceRanges(elementAtPointer: PsiElement): List<TextRange> {
  if (!elementAtPointer.isPhysical) {
    return emptyList()
  }
  var textOffset = elementAtPointer.textOffset
  val range = elementAtPointer.textRange
              ?: throw AssertionError("Null range for " + elementAtPointer + " of " + elementAtPointer.javaClass)
  if (textOffset < range.startOffset || textOffset < 0) {
    codeInsightLogger.error("Invalid text offset " + textOffset + " of element " + elementAtPointer + " of " + elementAtPointer.javaClass)
    textOffset = range.startOffset
  }
  return listOf(TextRange(textOffset, range.endOffset))
}

internal fun targetCtrlMouseData(
  ranges: List<TextRange>,
  isNavigatable: Boolean,
  target: DocumentationTarget?,
): CtrlMouseData = CtrlMouseData(
  ranges,
  isNavigatable,
  target?.computeDocumentationHint(),
  target,
)
