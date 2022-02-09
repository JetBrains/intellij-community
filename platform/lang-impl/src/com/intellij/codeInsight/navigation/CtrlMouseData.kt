// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("TestOnlyProblems") // KTIJ-19938

package com.intellij.codeInsight.navigation

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.navigation.BaseCtrlMouseInfo.getReferenceRanges
import com.intellij.lang.documentation.DocumentationTarget
import com.intellij.lang.documentation.psi.PsiElementDocumentationTarget
import com.intellij.lang.documentation.psi.isNavigatableQuickDoc
import com.intellij.lang.documentation.psi.psiDocumentationTarget
import com.intellij.lang.documentation.symbol.impl.symbolDocumentationTarget
import com.intellij.model.Symbol
import com.intellij.model.psi.PsiSymbolService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.HintText
import com.intellij.openapi.util.TextRange
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
      target = psiDocumentationTarget(psi)
               ?: PsiElementDocumentationTarget(psi.project, psi, elementAtOffset, null)
    )
  }
  return targetCtrlMouseData(
    ranges,
    true, // non-PSI are always navigatable
    target = symbolDocumentationTarget(project, symbol),
  )
}

internal fun psiCtrlMouseData(
  leafElement: PsiElement,
  targetElement: PsiElement,
): CtrlMouseData {
  return targetCtrlMouseData(
    ranges = getReferenceRanges(leafElement),
    isNavigatable = isNavigatableQuickDoc(leafElement, targetElement),
    target = psiDocumentationTarget(targetElement)
             ?: PsiElementDocumentationTarget(leafElement.project, targetElement, leafElement, null)
  )
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
