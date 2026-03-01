// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.logging.resolve

import com.intellij.codeInspection.logging.LOGGER_RESOLVE_TYPE_SEARCHERS
import com.intellij.codeInspection.logging.LoggingStringPartEvaluator
import com.intellij.codeInspection.logging.PlaceholderContext
import com.intellij.codeInspection.logging.PlaceholderEscapeSymbolStrategy
import com.intellij.codeInspection.logging.PlaceholderLoggerType.LOG4J_EQUAL_PLACEHOLDERS
import com.intellij.codeInspection.logging.PlaceholderLoggerType.LOG4J_FORMATTED_STYLE
import com.intellij.codeInspection.logging.PlaceholderLoggerType.LOG4J_OLD_STYLE
import com.intellij.codeInspection.logging.PlaceholderLoggerType.SLF4J
import com.intellij.codeInspection.logging.PlaceholderLoggerType.SLF4J_EQUAL_PLACEHOLDERS
import com.intellij.codeInspection.logging.PlaceholderRanges
import com.intellij.codeInspection.logging.PlaceholdersStatus
import com.intellij.codeInspection.logging.detectLoggerMethod
import com.intellij.codeInspection.logging.getPlaceholderContext
import com.intellij.codeInspection.logging.solvePlaceholderCount
import com.intellij.model.Symbol
import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.model.psi.PsiSymbolReferenceHints
import com.intellij.model.psi.PsiSymbolReferenceProvider
import com.intellij.model.search.SearchRequest
import com.intellij.openapi.project.Project
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UPolyadicExpression
import org.jetbrains.uast.expressions.UInjectionHost
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.toUElementOfType

class LoggingArgumentSymbolReferenceProvider : PsiSymbolReferenceProvider {
  override fun getReferences(element: PsiExternalReferenceHost, hints: PsiSymbolReferenceHints): Collection<PsiSymbolReference> {
    val literalExpression = element.toUElementOfType<UInjectionHost>() ?: return emptyList()
    return getLogArgumentReferences(literalExpression) ?: emptyList()
  }

  override fun getSearchRequests(project: Project, target: Symbol): Collection<SearchRequest> = emptyList()
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

  return getAdjustedPlaceholderList(loggerReferenceList, context)
}

/**
 * Retrieves the context of a placeholder in a logger statement.
 *
 * @param uExpression The UExpression representing the placeholder.
 * @return The PlaceholderContext object if the placeholder context is found, otherwise null.
 */
internal fun getContext(uExpression: UExpression): PlaceholderContext? {
  val uCallExpression = uExpression.getParentOfType<UCallExpression>() ?: return null
  val logMethod = detectLoggerMethod(uCallExpression) ?: return null

  val context = getPlaceholderContext(logMethod, LOGGER_RESOLVE_TYPE_SEARCHERS)
  if (uExpression != context?.logStringArgument || context.partHolderList.size != 1) return null
  return context
}

/**
 * Retrieves a list of placeholders, for which there is a resolve argument exists.
 * This list might be different from initial input, because, for example, the number of arguments is less,
 * than the number of placeholders or last argument could be an exception.
 * @param placeholderList The list of placeholders. It might be a text ranges or
 * @param context The placeholder context.
 * @return The adjusted list of placeholders, or null if the logger type is not supported.
 */
internal fun <T> getAdjustedPlaceholderList(placeholderList: List<T>, context: PlaceholderContext): List<T>? {
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

/**
 * Retrieves a list of placeholder ranges from the given `context`.
 *
 * @param context The [PlaceholderContext] object containing the necessary data for retrieving placeholder ranges.
 * @return A list of PlaceholderRanges or null if the logStringArgument is null or the number of placeholders is not exact.
 * @see PlaceholderContext
 * @see PlaceholderRanges
 */
internal fun getPlaceholderRanges(context: PlaceholderContext): List<PlaceholderRanges>? {
  val logStringText = context.logStringArgument.sourcePsi?.text ?: return null
  val type = if (isKotlinMultilineString(context.logStringArgument, logStringText)) {
    PlaceholderEscapeSymbolStrategy.KOTLIN_RAW_MULTILINE_STRING
  }
  else {
    PlaceholderEscapeSymbolStrategy.RAW_STRING
  }

  val partHolders = listOf(
    LoggingStringPartEvaluator.PartHolder(
      logStringText,
      true
    )
  )
  val placeholderCountResult = solvePlaceholderCount(context.loggerType, context.placeholderParameters.size, partHolders, type)
  if (placeholderCountResult.status != PlaceholdersStatus.EXACTLY) return null

  return placeholderCountResult.placeholderRangesList
}

private fun isKotlinString(logString: UExpression): Boolean {
  return logString is UPolyadicExpression
}

private fun isKotlinMultilineString(logString: UExpression, text: String): Boolean {
  return isKotlinString(logString) && text.startsWith("\"\"\"") && text.endsWith("\"\"\"") && text.length >= 6
}