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
  val ranges = getPlaceholderRanges(context) ?: return null

  val psiLiteralExpression = uExpression.sourcePsi ?: return null

  val loggerReferenceList = ranges.zip(context.placeholderParameters) { placeholderRanges, parameter ->
    val parameterPsi = parameter.sourcePsi ?: return null
    placeholderRanges.ranges.map { range ->
      if (range == null) return null
      LoggingArgumentSymbolReference(psiLiteralExpression, range, parameterPsi)
    }
  }.flatten()

  return getAlignedPlaceholderCount(loggerReferenceList, context)
}

internal fun getContext(uExpression: UExpression): PlaceholderContext? {
  val uCallExpression = uExpression.getParentOfType<UCallExpression>() ?: return null
  val logMethod = detectLoggerMethod(uCallExpression) ?: return null

  val context = getPlaceholderContext(logMethod, LOGGER_RESOLVE_TYPE_SEARCHERS)
  if (uExpression != context?.logStringArgument || context.partHolderList.size != 1) return null
  return context
}

internal fun <T> getAlignedPlaceholderCount(placeholderList: List<T>, context: PlaceholderContext): List<T>? {
  val placeholderParametersSize = context.placeholderParameters.size
  return when (context.loggerType) {
    SLF4J -> {
      placeholderList.take(if (context.lastArgumentIsException) placeholderParametersSize - 1 else placeholderParametersSize)
    }
    LOG4J_OLD_STYLE, LOG4J_FORMATTED_STYLE -> {
      if (context.lastArgumentIsException && placeholderParametersSize == 1) {
        emptyList()
      }
      else {
        placeholderList
      }
    }
    SLF4J_EQUAL_PLACEHOLDERS, LOG4J_EQUAL_PLACEHOLDERS -> {
      placeholderList
    }
    else -> null
  }
}

internal fun getPlaceholderRanges(context: PlaceholderContext): List<PlaceholderRanges>? {
  val logStringText = context.logStringArgument.sourcePsi?.text ?: return null
  val partHolders = listOf(
    LoggingStringPartEvaluator.PartHolder(
      logStringText,
      true
    )
  )
  val placeholderCountResult = solvePlaceholderCount(context.loggerType, context.placeholderParameters.size, partHolders)
  if (placeholderCountResult.status != PlaceholdersStatus.EXACTLY) return null

  return placeholderCountResult.placeholderRangesList
}