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

class JvmLoggerSymbolReferenceProvider : PsiSymbolReferenceProvider {
  override fun getReferences(element: PsiExternalReferenceHost, hints: PsiSymbolReferenceHints): Collection<PsiSymbolReference> {
    if (!hintsCheck(hints)) return emptyList()

    val literalExpression = element.toUElementOfType<UExpression>() ?: return emptyList()
    return getLogArgumentReferences(literalExpression) ?: emptyList()
  }

  private fun hintsCheck(hints: PsiSymbolReferenceHints): Boolean {
    if (!hints.referenceClass.isAssignableFrom(LoggingArgumentSymbolReference::class.java)) return false
    val targetClass = hints.targetClass
    if (targetClass != null && !targetClass.isAssignableFrom(LoggingArgumentSymbol::class.java)) return false
    val target = hints.target
    return target == null || target is LoggingArgumentSymbol
  }

  override fun getSearchRequests(project: Project, target: Symbol): Collection<SearchRequest> {
    return listOf()
  }
}

fun getLogArgumentReferences(uExpression: UExpression): List<PsiSymbolReference>? {
  val uCallExpression = uExpression.getParentOfType<UCallExpression>() ?: return null
  val log4jHasImplementationForSlf4j = LoggingUtil.hasBridgeFromSlf4jToLog4j2(uCallExpression)

  val logMethod = detectLoggerMethod(uCallExpression) ?: return null

  val context = getPlaceholderContext(logMethod, LOGGER_RESOLVE_TYPE_SEARCHERS, log4jHasImplementationForSlf4j) ?: return null
  if (uExpression != context.logStringArgument || context.partHolderList.size > 1) return null

  val placeholderCountResult = solvePlaceholderCount(context.loggerType, context.placeholderParameters.size, context.partHolderList)
  if (placeholderCountResult.status != PlaceholdersStatus.EXACTLY) return null

  val rangeWithParameterList = placeholderCountResult.placeholderRangeList.zip(context.placeholderParameters)
  val psiLiteralExpression = uExpression.sourcePsi ?: return null


  val placeholderParametersSize = context.placeholderParameters.size
  val loggerReferenceList = rangeWithParameterList.map { (range, parameter) ->
    if (range == null) return null
    val alignedRange = getAlignedRangeInLiteralExpression(uExpression, range) ?: return null
    val parameterPsi = parameter.sourcePsi ?: return null
    LoggingArgumentSymbolReference(psiLiteralExpression, alignedRange, parameterPsi)
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