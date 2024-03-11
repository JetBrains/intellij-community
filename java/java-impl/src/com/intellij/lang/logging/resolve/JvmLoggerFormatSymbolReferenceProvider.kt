// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.logging.resolve

import com.intellij.lang.logging.resolve.PlaceholderLoggerType.SLF4J
import com.intellij.model.Symbol
import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.model.psi.PsiSymbolReferenceHints
import com.intellij.model.psi.PsiSymbolReferenceProvider
import com.intellij.model.search.SearchRequest
import com.intellij.openapi.project.Project
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.toUElementOfType

class JvmLoggerFormatSymbolReferenceProvider : PsiSymbolReferenceProvider {
  override fun getReferences(element: PsiExternalReferenceHost, hints: PsiSymbolReferenceHints): Collection<PsiSymbolReference> {
    if (!hintsCheck(hints)) return listOf()

    val literalExpression = element.toUElementOfType<UExpression>() ?: return listOf()
    return getLogArgumentReferences(literalExpression)
  }

  private fun hintsCheck(hints: PsiSymbolReferenceHints): Boolean {
    if (!hints.referenceClass.isAssignableFrom(JvmLoggerArgumentSymbolReference::class.java)) return false
    val targetClass = hints.targetClass
    if (targetClass != null && !targetClass.isAssignableFrom(JvmLoggerArgumentSymbol::class.java)) return false
    val target = hints.target
    return target == null || target is JvmLoggerArgumentSymbol
  }

  override fun getSearchRequests(project: Project, target: Symbol): Collection<SearchRequest> {
    return listOf()
  }
}

fun getLogArgumentReferences(literalExpression: UExpression): List<PsiSymbolReference> {
  val node = literalExpression.getParentOfType<UCallExpression>() ?: return emptyList()
  val searcher = LOGGER_RESOLVE_TYPE_SEARCHERS.mapFirst(node) ?: return emptyList()

  val arguments = node.valueArguments

  if (arguments.isEmpty() && searcher != SLF4J_BUILDER_HOLDER) return emptyList()

  val log4jAsImplementationForSlf4j = LoggingUtil.hasBridgeFromSlf4jToLog4j2(node)
  val loggerType = searcher.findType(node, LoggerContext(log4jAsImplementationForSlf4j)) ?: return emptyList()

  val placeholderCountContext = getPlaceholderCountContext(node, searcher, loggerType) ?: return emptyList()

  val parts = collectParts(placeholderCountContext.logStringArgument) ?: return emptyList()

  if (parts.size > 1) {
    return emptyList()
  }
  val placeholderCountResult = solvePlaceholderCount(loggerType, placeholderCountContext.placeholderParameters.size, parts)

  if (placeholderCountResult.status != PlaceholdersStatus.EXACTLY || placeholderCountResult.placeholderRangesInPartHolderList.size != 1) {
    return emptyList()
  }


  val placeholderRanges = placeholderCountResult.placeholderRangesInPartHolderList.single()

  val parameterExpressions = placeholderCountContext.placeholderParameters

  val zipped = placeholderRanges.rangeList.zip(parameterExpressions)

  val literalExpressionPsi = literalExpression.sourcePsi ?: return emptyList()

  val result = when (loggerType) {
    SLF4J -> {
       zipped.mapNotNull {(range, parameter) ->
         if (range == null) return@mapNotNull null
         val parameterPsi = parameter.sourcePsi ?: return@mapNotNull null
        JvmLoggerArgumentSymbolReference(literalExpressionPsi, range, parameterPsi)
      }
    }
    else -> {
      emptyList()
    }
  }
  return result
}