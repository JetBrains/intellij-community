// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.logging.resolve

import com.intellij.codeInspection.logging.*
import com.intellij.codeInspection.logging.PlaceholderLoggerType.*
import com.intellij.model.Symbol
import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.model.psi.PsiSymbolReferenceHints
import com.intellij.model.psi.PsiSymbolReferenceProvider
import com.intellij.model.search.SearchRequest
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiLiteralExpression
import com.siyeh.ig.psiutils.ExpressionUtils
import org.jetbrains.uast.*

class LoggingArgumentSymbolReferenceProvider : PsiSymbolReferenceProvider {
  override fun getReferences(element: PsiExternalReferenceHost, hints: PsiSymbolReferenceHints): Collection<PsiSymbolReference> {
    val literalExpression = element.toUElementOfType<UExpression>() ?: return emptyList()
    if (literalExpression !is ULiteralExpression && literalExpression !is UPolyadicExpression) return emptyList()
    return getLogArgumentReferences(literalExpression) ?: emptyList()
  }

  override fun getSearchRequests(project: Project, target: Symbol): Collection<SearchRequest> {
    return listOf()
  }
}

fun getLogArgumentReferences(uExpression: UExpression): List<PsiSymbolReference>? {
  val context = getContext(uExpression) ?: return null
  val ranges = getAlignedRanges(uExpression, context) ?: return null

  val psiLiteralExpression = uExpression.sourcePsi ?: return null
  val placeholderParametersSize = context.placeholderParameters.size

  val loggerReferenceList = ranges.zip(context.placeholderParameters) { range, parameter ->
    val parameterPsi = parameter.sourcePsi ?: return null
    LoggingArgumentSymbolReference(psiLiteralExpression, range, parameterPsi)
  }

  return when (context.loggerType) {
    SLF4J -> {
      loggerReferenceList.take(if (context.lastArgumentIsException) placeholderParametersSize - 1 else placeholderParametersSize)
    }
    LOG4J_OLD_STYLE, LOG4J_FORMATTED_STYLE -> {
      if (context.lastArgumentIsException && placeholderParametersSize == 1) {
        emptyList()
      }
      else {
        loggerReferenceList
      }
    }
    SLF4J_EQUAL_PLACEHOLDERS, LOG4J_EQUAL_PLACEHOLDERS -> {
      loggerReferenceList
    }
    else -> null
  }
}

internal fun getContext(uExpression: UExpression): PlaceholderContext? {
  val uCallExpression = uExpression.getParentOfType<UCallExpression>() ?: return null
  val logMethod = detectLoggerMethod(uCallExpression) ?: return null

  val context = getPlaceholderContext(logMethod, LOGGER_RESOLVE_TYPE_SEARCHERS)
  if (uExpression != context?.logStringArgument || context.partHolderList.size > 1) return null
  return context
}

internal fun getAlignedRanges(uExpression: UExpression, context: PlaceholderContext) : List<TextRange>? {
  val placeholderCountResult = solvePlaceholderCount(context.loggerType, context.placeholderParameters.size, context.partHolderList)
  if (placeholderCountResult.status != PlaceholdersStatus.EXACTLY) return null

  return placeholderCountResult.placeholderRangeList.map { range ->
    if (range == null) return null
    getAlignedRangeInLiteralExpression(uExpression, range) ?: return null
  }
}

private fun getAlignedRangeInLiteralExpression(uExpression: UExpression, range: TextRange?): TextRange? {
  if (range == null) return null
  val psiLiteralExpression = uExpression.sourcePsi ?: return null
  return if (psiLiteralExpression is PsiLiteralExpression) {
    ExpressionUtils.findStringLiteralRange(psiLiteralExpression, range.startOffset, range.endOffset)
  }
  else {
    val text = psiLiteralExpression.text
    if (text == null) return null
    val value = uExpression.evaluateString() ?: return null
    val offset = text.indexOf(value)
    if (offset == -1) return null
    range.shiftRight(offset)
  }
}